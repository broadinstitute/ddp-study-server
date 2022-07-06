package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lombok.Getter;
import lombok.Setter;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.export.ParticipantExcelGenerator;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.ParticipantColumn;
import org.broadinstitute.dsm.model.elastic.export.excel.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantRecordData;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    public DownloadParticipantListRoute() {
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        // requests for the new export capability will always have 'splitOptions' as a request param
        if (request.queryMap().hasKey("splitOptions")) {
            return processRequestNew(request, response, userId);
        }

        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        List<ParticipantColumn> columnNames = payload.getColumnNames();
        Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column);
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>()).add(column);
        });
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;
        ParticipantRecordData rowData = new ParticipantRecordData(columnAliasEsPathMap);
        Filterable filterable = FilterFactory.of(request);
        //counting phase
        while (true) {
            filterable.setFrom(currentFrom);
            filterable.setTo(currentTo);
            ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(request.queryMap());
            rowData.processData(filteredList, true);
            if (filteredList.getTotalCount() < currentFrom) {
                break;
            }
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }

        //data processing
        currentFrom = DEFAULT_FROM;
        currentTo = MAX_RESULT_SIZE;
        ParticipantExcelGenerator generator = new ParticipantExcelGenerator();
        generator.createHeader(rowData.getHeader());
        int columnsNumber;
        while (true) {
            filterable.setFrom(currentFrom);
            filterable.setTo(currentTo);
            ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(request.queryMap());
            List<List<String>> participantRecords = rowData.processData(filteredList, false);
            columnsNumber = getColumnsNumber(participantRecords);
            if (filteredList.getTotalCount() < currentFrom) {
                break;
            }
            generator.appendData(participantRecords);
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }
        generator.formatSizes(columnsNumber);
        generator.writeInResponse(response);

        return response.raw();
    }

    private int getColumnsNumber(List<List<String>> participantRecords) {
        if (participantRecords.isEmpty()) {
            return 0;
        }
        return participantRecords.get(0).size();
    }


    /**
     * Generates a file for download.  When removing the feature-flag-export-new role, processRequestNew should
     * be renamed to processRequest, and the old 'processRequest' method should be deleted
     */
    public Object processRequestNew(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayloadNew payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayloadNew.class);
        DownloadParticipantListParams params = new DownloadParticipantListParams(request.queryMap());

        String realm = RoutePath.getRealm(request);
        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance,
                params.splitOptions, params.onlyMostRecent);

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, request.queryMap());
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participants);

        TabularParticipantExporter exporter = TabularParticipantExporter.getExporter(exportConfigs,
                participantValueMaps, params.fileFormat);
        exporter.export(response);
        return response.raw();
    }

    /** Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE  */
    private static List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
        List<ParticipantWrapperDto> allResults = new ArrayList<ParticipantWrapperDto>();
        int currentFrom = DEFAULT_FROM;
        int currentTo = MAX_RESULT_SIZE;

        while (true) {
            // For each batch of results, add the DTOs to the allResults list
            filter.setFrom(currentFrom);
            filter.setTo(currentTo);
            ParticipantWrapperResult filteredSubset = (ParticipantWrapperResult) filter.filter(queryParamsMap);
            allResults.addAll(filteredSubset.getParticipants());
            // if the total count is less than the range we are currently on, stop fetching
            if (filteredSubset.getTotalCount() < currentTo) {
                break;
            }
            currentFrom = currentTo;
            currentTo += MAX_RESULT_SIZE;
        }

        return allResults;
    }



    @Getter
    @Setter
    /** on retirement of feature-flag-export-new, this class should be promoted to DownloadParticipantListPayload */
    private static class DownloadParticipantListPayloadNew {
        private List<Filter> columnNames;
    }

    @Getter
    @Setter
    private static class DownloadParticipantListParams {
        private static final List<String> allowedFileFormats = Arrays.asList("tsv", "xlsx");
        private boolean splitOptions = true;
        private boolean onlyMostRecent = false;
        private String fileFormat = "tsv";

        public DownloadParticipantListParams(QueryParamsMap paramMap) {
            if (paramMap.hasKey("fileFormat")) {
                String fileFormatParam = paramMap.get("fileFormat").value();
                if (allowedFileFormats.contains(fileFormatParam)) {
                    fileFormat = fileFormatParam;
                }
            }
            if (paramMap.hasKey("splitOptions")) {
                splitOptions = Boolean.valueOf(paramMap.get("splitOptions").value());
            }
            if (paramMap.hasKey("splitOptions")) {
                onlyMostRecent = Boolean.valueOf(paramMap.get("onlyMostRecent").value());
            }
        }
    }


}

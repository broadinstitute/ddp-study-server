package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import com.google.common.net.MediaType;
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
import org.broadinstitute.dsm.util.UserUtil;
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
        if (UserUtil.checkUserAccess(null, userId, "feature_flag_export_new", null)) {
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


    public Object processRequestNew(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayloadNew payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayloadNew.class);

        String realm = RoutePath.getRealm(request);
        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance);

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, request.queryMap());
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participants);

        response.type(MediaType.TSV_UTF_8.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + getExportFilename());

        TabularParticipantExporter exporter = new TabularParticipantExporter(exportConfigs, participantValueMaps);
        exporter.writeTable(response.raw().getWriter());
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

    private static final String FILE_DATE_FORMAT = "yyyy-MM-dd";
    private static String getExportFilename() {
        LocalDate date = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(FILE_DATE_FORMAT);
        String exportFileName = String.format("Participant-%s.tsv", date.format(formatter));
        return exportFileName;
    }

    @Getter
    @Setter
    /** on retirement of feature-flag-export-new, this class should be promoted to DownloadParticipantListPayload */
    private static class DownloadParticipantListPayloadNew {
        private List<Filter> columnNames;
    }


}

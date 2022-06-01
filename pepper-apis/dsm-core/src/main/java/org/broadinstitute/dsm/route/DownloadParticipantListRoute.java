package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.broadinstitute.dsm.export.ParticipantExcelGenerator;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantRecordData;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.elastic.export.excel.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    public DownloadParticipantListRoute() {
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        List<Filter> columnNames = payload.getColumnNames();
        Set<String> options = columnNames.stream().map(Filter::getType).collect(Collectors.toSet());
        Map<Alias, List<Filter>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column.getParticipantColumn());
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>())
                    .add(column);
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


}

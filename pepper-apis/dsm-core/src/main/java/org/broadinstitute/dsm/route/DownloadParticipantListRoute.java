package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import org.broadinstitute.dsm.export.ParticipantExcelGenerator;
import org.broadinstitute.dsm.model.Filter;
import org.broadinstitute.dsm.model.elastic.export.excel.*;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
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
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        List<Filter> columnNames = payload.getColumnNames();
        Map<Alias, List<Filter>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column.getParticipantColumn());
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>())
                    .add(column);
        });

        Filterable filterable = FilterFactory.of(request);

        List<ExcelRow> participantRows = fetchParticipantRows(filterable, request.queryMap(), columnAliasEsPathMap);

        ParticipantExcelGenerator generator = new ParticipantExcelGenerator();
        for(ExcelRow row : participantRows) {
            generator.appendData(row);
        }
        generator.writeInResponse(response);
        return response.raw();
    }

    /** Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE  */
    private List<ExcelRow> fetchParticipantRows(Filterable filter, QueryParamsMap queryParamsMap, Map<Alias, List<Filter>> columnAliasEsPathMap) {
        ParticipantRecordData rowData = new ParticipantRecordData(columnAliasEsPathMap);
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

        List<ExcelRow> participantRows = rowData.processToExcel(allResults);
        return participantRows;
    }
}

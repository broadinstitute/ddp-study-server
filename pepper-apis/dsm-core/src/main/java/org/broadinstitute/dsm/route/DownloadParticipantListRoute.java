package org.broadinstitute.dsm.route;

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
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantConsumer;
import org.broadinstitute.dsm.model.elastic.export.excel.ParticipantProducer;
import org.broadinstitute.dsm.model.elastic.export.excel.ExcelRow;
import org.broadinstitute.dsm.model.elastic.sort.Alias;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.elastic.export.excel.DownloadParticipantListPayload;
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
        Map<Alias, List<Filter>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column.getParticipantColumn());
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>())
                    .add(column);
        });
        BlockingQueue<ExcelRow> participantRows = new ArrayBlockingQueue<>(MAX_RESULT_SIZE);
        Filterable filterable = FilterFactory.of(request);
        new ParticipantProducer(filterable, request.queryMap(), columnAliasEsPathMap,
                participantRows).start();
        ParticipantExcelGenerator generator = new ParticipantConsumer(participantRows).get();
        generator.writeInResponse(response);
        return response.raw();
    }

}

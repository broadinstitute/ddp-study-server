package org.broadinstitute.dsm.route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.broadinstitute.dsm.export.ExcelUtils;
import org.broadinstitute.dsm.model.ParticipantColumn;
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
        List<ParticipantColumn> columnNames = payload.getColumnNames();
        Map<Alias, List<ParticipantColumn>> columnAliasEsPathMap =
                new TreeMap<>(Comparator.comparing(Alias::isCollection).thenComparing(Alias::getValue));
        columnNames.forEach(column -> {
            Alias alias = Alias.of(column);
            columnAliasEsPathMap.computeIfAbsent(alias, paths -> new ArrayList<>())
                    .add(column);
        });

        Filterable filterable = FilterFactory.of(request);
        ParticipantWrapperResult filteredList = (ParticipantWrapperResult) filterable.filter(request.queryMap());

        ParticipantRecordData rowData = new ParticipantRecordData(filteredList, columnAliasEsPathMap);
        rowData.processData();
        ExcelUtils.createResponseFile(rowData, response);
        return response.raw();
    }


}

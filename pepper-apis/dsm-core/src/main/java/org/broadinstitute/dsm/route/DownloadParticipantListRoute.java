package org.broadinstitute.dsm.route;

import static org.broadinstitute.dsm.util.ElasticSearchUtil.DEFAULT_FROM;
import static org.broadinstitute.dsm.util.ElasticSearchUtil.MAX_RESULT_SIZE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperDto;
import org.broadinstitute.dsm.model.participant.ParticipantWrapperResult;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(DownloadParticipantListRoute.class);

    /**
     * Generates a file for download.  When removing the feature-flag-export-new role, processRequestNew should
     * be renamed to processRequest, and the old 'processRequest' method should be deleted
     */
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (!UserUtil.checkUserAccess(null, userId, "pt_list_export", null)) {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        DownloadParticipantListParams params = new DownloadParticipantListParams(request.queryMap());

        String realm = RoutePath.getRealm(request);
        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);

        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance,
                params.isSplitOptions(), params.isOnlyMostRecent());

        Filterable filterable = FilterFactory.of(request);
        List<ParticipantWrapperDto> participants = fetchParticipantEsData(filterable, request.queryMap());
        logger.info("Beginning parse of " + participants.size() + "participants");
        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();
        List<Map<String, String>> participantValueMaps = parser.parse(exportConfigs, participants);

        TabularParticipantExporter exporter = TabularParticipantExporter.getExporter(exportConfigs,
                participantValueMaps, params.getFileFormat());
        exporter.export(response);
        return response.raw();
    }

    /**
     * Fetches participant information from ElasticSearch in batches of MAX_RESULT_SIZE
     */
    private List<ParticipantWrapperDto> fetchParticipantEsData(Filterable filter, QueryParamsMap queryParamsMap) {
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


}

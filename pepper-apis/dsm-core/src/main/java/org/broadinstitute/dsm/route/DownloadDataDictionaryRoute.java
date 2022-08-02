package org.broadinstitute.dsm.route;

import java.util.List;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.model.elastic.export.tabular.DataDictionaryExporter;
import org.broadinstitute.dsm.model.elastic.export.tabular.ModuleExportConfig;
import org.broadinstitute.dsm.model.elastic.export.tabular.TabularParticipantParser;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class DownloadDataDictionaryRoute extends RequestHandler {
    private static final Logger logger = LoggerFactory.getLogger(DownloadDataDictionaryRoute.class);

    /**
     * Generates a file for download.  When removing the feature-flag-export-new role, processRequestNew should
     * be renamed to processRequest, and the old 'processRequest' method should be deleted
     */
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        DownloadParticipantListPayload payload =
                ObjectMapperSingleton.instance().readValue(request.body(), DownloadParticipantListPayload.class);
        DownloadParticipantListParams params = new DownloadParticipantListParams(request.queryMap());

        String realm = RoutePath.getRealm(request);
        String userIdReq = UserUtil.getUserId(request);

        DDPInstance instance = DDPInstance.getDDPInstanceWithRole(realm, DBConstants.MEDICAL_RECORD_ACTIVATED);
        TabularParticipantParser parser = new TabularParticipantParser(payload.getColumnNames(), instance,
                params.isSplitOptions(), params.isOnlyMostRecent());

        List<ModuleExportConfig> exportConfigs = parser.generateExportConfigs();

        DataDictionaryExporter exporter = new DataDictionaryExporter(exportConfigs);
        exporter.export(response);
        return response.raw();
    }
}

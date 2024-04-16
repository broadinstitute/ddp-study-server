package org.broadinstitute.dsm.route;

import com.google.common.net.MediaType;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListParams;
import org.broadinstitute.dsm.model.participant.DownloadParticipantListPayload;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.download.DownloadParticipantListService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import spark.Request;
import spark.Response;

public class DownloadParticipantListRoute extends RequestHandler {

    DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();

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
        if (!UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdReq)) {
            response.status(403);
            return UserErrorMessages.NO_RIGHTS;
        }
        setResponseHeaders(response, realm + "_export.zip");
        Filterable filterable = FilterFactory.of(request);
        DDPInstanceDto ddpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(realm)
                .orElseThrow(() -> new IllegalArgumentException("No DDP instance found for realm " + realm));
        return DownloadParticipantListService.createParticipantDownloadZip(filterable, params, ddpInstanceDto, request.queryMap(),
                payload.getColumnNames(), response);
    }

    protected void setResponseHeaders(Response response, String filename) {
        response.type(MediaType.OCTET_STREAM.toString());
        response.header("Access-Control-Expose-Headers", "Content-Disposition");
        response.header("Content-Disposition", "attachment;filename=" + filename);
    }

}

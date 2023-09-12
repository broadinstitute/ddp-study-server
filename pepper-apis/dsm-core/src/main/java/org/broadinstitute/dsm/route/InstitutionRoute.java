package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.exception.AuthorizationException;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class InstitutionRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String requestBody = RouteUtil.requireRequestBody(request);

        if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
            // TODO we should get rid of this code and the things it calls if it is obsolete.
            log.error("InstitutionRoute POST called (expecting this request METHOD to be obsolete)");

            JsonObject jsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
            String user = RouteUtil.requireStringFromJsonObject(jsonObject, RequestParameter.USER_ID);
            String ddpParticipantId = RouteUtil.requireStringFromJsonObject(jsonObject, RequestParameter.DDP_PARTICIPANT_ID);
            String realm = RouteUtil.requireStringFromJsonObject(jsonObject, RequestParameter.DDP_REALM);

            if (!UserUtil.checkUserAccess(realm, userId, "mr_view", user)) {
                throw new AuthorizationException();
            }
            DDPInstance ddpInstance = DDPInstance.getDDPInstance(realm);
            if (ddpInstance == null) {
                throw new DsmInternalError("Invalid realm " + realm);
            }
            return MedicalRecord.getDDPInstitutionInfo(ddpInstance, ddpParticipantId);

        } else if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            String realm = RouteUtil.requireRealm(request);
            UpdateDestructionPolicyRequest req;
            try {
                req = new Gson().fromJson(requestBody, UpdateDestructionPolicyRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", requestBody);
                throw new DSMBadRequestException("Invalid request format");
            }

            String user = RouteUtil.requireParam(RequestParameter.USER_ID, req.getUserId());
            if (!UserUtil.checkUserAccess(null, userId, "mr_request", user)) {
                throw new AuthorizationException();
            }

            OncHistoryDetail.updateDestructionPolicy(
                    RouteUtil.requireParam(RequestParameter.POLICY, req.getPolicy()),
                    RouteUtil.requireParam(RequestParameter.FACILITY, req.getFacility()),
                    realm,
                    RouteUtil.requireParam(RequestParameter.USER_MAIL, req.getUserMail())
            );
            return new Result(200);
        }
        RouteUtil.handleInvalidRouteMethod(request, "InstitutionRoute");
        return null;
    }

    @Data
    private static class UpdateDestructionPolicyRequest {
        private String facility;
        private String policy;
        private String realm;
        private String userId;
        private String userMail;
    }
}

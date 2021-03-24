package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class KitAuthorizationRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitAuthorizationRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String kitRequestId = null;
        if (queryParams.value(RequestParameter.KIT_REQUEST_ID) != null) {
            kitRequestId = queryParams.get(RequestParameter.KIT_REQUEST_ID).value();
        }
        if (StringUtils.isNotBlank(kitRequestId)) {
            if (queryParams.value(RequestParameter.ACTIVATE) == null) {
                throw new RuntimeException("No Authorization value sent");
            }
            boolean authorize = queryParams.get(RequestParameter.ACTIVATE).booleanValue();
            KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);
            String realm = kitRequest.getRealm();
            String userIdRequest = UserUtil.getUserId(request);
            if (!userId.equals(userIdRequest)) {
                throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
            }

            if (UserUtil.checkUserAccess(realm, userId, "kit_authorization")) {
                if (authorize) {
                    KitRequestShipping.changeAuthorizationStatus(kitRequestId, null, userIdRequest, true);
                }
                else {
                    JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
                    String reason = jsonObject.get("reason").getAsString();
                    KitRequestShipping.changeAuthorizationStatus(kitRequestId, reason, userIdRequest, false);
                }
                return new Result(200);
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        else {
            throw new RuntimeException("KitRequestId id was missing");
        }
    }
}

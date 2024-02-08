package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class KitAuthorizationRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();

        if (StringUtils.isNotBlank(queryParams.value(RequestParameter.KIT_REQUEST_ID))) {
            int kitRequestId = Integer.parseInt(queryParams.get(RequestParameter.KIT_REQUEST_ID).value());
            if (queryParams.value(RequestParameter.ACTIVATE) == null) {
                throw new DSMBadRequestException("No Authorization value sent");
            }
            boolean authorize = queryParams.get(RequestParameter.ACTIVATE).booleanValue();
            KitRequestShipping kitRequest = KitRequestShipping.getKitRequest(kitRequestId);
            String realm = kitRequest.getRealm();
            String userIdRequest = UserUtil.getUserId(request);
            if (UserUtil.checkUserAccess(realm, userId, "kit_authorization", userIdRequest)) {
                if (authorize) {
                    KitRequestShipping.changeAuthorizationStatus(kitRequestId, null, userIdRequest, true);
                } else {
                    JsonObject jsonObject = new JsonParser().parse(request.body()).getAsJsonObject();
                    String reason = jsonObject.get("reason").getAsString();
                    KitRequestShipping.changeAuthorizationStatus(kitRequestId, reason, userIdRequest, false);
                }
                return new Result(200);
            } else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        } else {
            throw new DSMBadRequestException("KitRequestId id was missing");
        }
    }
}

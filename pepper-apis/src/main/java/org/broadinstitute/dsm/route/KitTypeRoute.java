package org.broadinstitute.dsm.route;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.KitType;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.Request;
import spark.Response;

public class KitTypeRoute extends RequestHandler {

    private final KitUtil kitUtil;

    public KitTypeRoute(@NonNull KitUtil kitUtil) {
        this.kitUtil = kitUtil;
    }

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        if (request.url().contains(RoutePath.KIT_TYPE)) {
            if (UserUtil.checkUserAccess(realm, userId, "kit_shipping") || UserUtil.checkUserAccess(realm, userId, "kit_shipping_view")) {
                if (StringUtils.isNotBlank(realm)) {
                    String userIdRequest = UserUtil.getUserId(request);
                    if (!userId.equals(userIdRequest)) {
                        throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                    }
                    return KitType.getKitTypes(realm, userIdRequest);
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        else {
            if(request.url().contains(RoutePath.UPLOAD_REASONS)){
                if (UserUtil.checkUserAccess(realm, userId, "kit_upload") || UserUtil.checkUserAccess(realm, userId, "kit_shipping_view")) {
                    if (StringUtils.isNotBlank(realm)) {
                        String userIdRequest = UserUtil.getUserId(request);
                        if (!userId.equals(userIdRequest)) {
                            throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                        }
                        return KitType.getUploadReasons(realm);
                    }
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        throw new RuntimeException("Realm is missing");
    }
}

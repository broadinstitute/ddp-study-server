package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.UserSettings;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class UserSettingRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(UserSettingRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        if (queryParams.value(UserUtil.USER_ID) != null) {
            String userIdRequest = queryParams.get(UserUtil.USER_ID).value();
            if (UserUtil.checkUserAccess(null, userId, "kit_shipping") || UserUtil.checkUserAccess(null, userId, "mr_view")
                    || UserUtil.checkUserAccess(null, userId, "pt_list_view")) {
                if (StringUtils.isNotBlank(userIdRequest)) {
                    String requestBody = request.body();
                    if (!userId.equals(userIdRequest)) {
                        throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
                    }
                    UserSettings userSettings = new Gson().fromJson(requestBody, UserSettings.class);
                    UserSettings.editUserSettings(userIdRequest, userSettings);
                    return new Result(200);
                }
                else {
                    logger.error("User id was blank");
                    return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
                }
            }
            else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        else {
            logger.error("User id was missing");
            return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
        }
    }
}

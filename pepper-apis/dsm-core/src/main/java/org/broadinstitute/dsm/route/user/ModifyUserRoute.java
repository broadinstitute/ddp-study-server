package org.broadinstitute.dsm.route.user;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.roles.UserRoleDao;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class ModifyUserRoute extends RequestHandler {

    Logger logger = LoggerFactory.getLogger(ModifyUserRoute.class);
    UserRoleDao userRoleDao;

    public ModifyUserRoute(UserRoleDao userRoleDao) {
        this.userRoleDao = userRoleDao;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }

        if (StringUtils.isNotBlank(realm)) {
            if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, DBConstants.USER_ACCESS, null)) {
                    String requestBody = request.body();
                    JsonObject object = new Gson().fromJson(requestBody, JsonObject.class);
                    if (!object.has("userId")) {
                        throw new RuntimeException("userId missing!");
                    }
                    if (!object.has("roleId")) {
                        throw new RuntimeException("roleId missing!");
                    }
                    if (!object.has("umbrellaId")) {
                        throw new RuntimeException("umbrellaId missing!");
                    }
                    logger.info("Going to update role for user id " + object.get("userId").getAsLong() + " to " +
                            object.get("roleId").getAsLong());
                    userRoleDao.updateNewRole(object.get("userId").getAsLong(), object.get("roleId").getAsLong(),
                            object.get("umbrellaId").getAsLong());

                    return new Result(200);
                } else {
                    response.status(401);
                    return new Result(401, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        logger.error("realm is empty");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }

}

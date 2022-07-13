package org.broadinstitute.dsm.route.user.patch;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class DeactivateUserRoute extends RequestHandler {

    UserDao userDao;

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
            if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, DBConstants.USER_DELETE, null)) {
                    String requestBody = request.body();
                    JsonObject jsonObject = new JsonParser().parse(requestBody).getAsJsonObject();
                    Long toBeDeletedUserId = jsonObject.get("userId").getAsLong();
                    if (toBeDeletedUserId == null) {
                        throw new RuntimeException("user Id should not be null");
                    }
                    log.info("Going to deactivate user id " + toBeDeletedUserId);
                    userDao.deactivateUser(toBeDeletedUserId);
                    return new Result(200);
                } else {
                    response.status(401);
                    return new Result(401, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        log.error("realm is empty");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}

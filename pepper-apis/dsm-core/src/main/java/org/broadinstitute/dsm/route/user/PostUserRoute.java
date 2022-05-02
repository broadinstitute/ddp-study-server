package org.broadinstitute.dsm.route.user;

import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class PostUserRoute extends RequestHandler {
    private static Logger logger = LoggerFactory.getLogger(PostUserRoute.class);
    String auth0Account;
    String clientKey;
    private UserDao userDao;

    public PostUserRoute(UserDao userDao, String auth0Account, String clientKey) {
        this.userDao = userDao;
        this.auth0Account = auth0Account;
        this.clientKey = clientKey;
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
                if (UserUtil.checkUserAccess(realm, userId, "user_add", null)) {
                    String requestBody = request.body();
                    UserDto newUser = new Gson().fromJson(requestBody, UserDto.class);
                    DDPInstance ddpInstance = DDPInstance.getDDPInstanceByRealmOrGuid(realm);
                    userDao.insertNewUser(auth0Account, clientKey, newUser, ddpInstance);
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

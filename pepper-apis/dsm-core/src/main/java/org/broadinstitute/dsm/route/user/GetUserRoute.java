package org.broadinstitute.dsm.route.user;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.DDPInstance;
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

public class GetUserRoute extends RequestHandler {
    private static Logger logger = LoggerFactory.getLogger(GetUserRoute.class);
    private UserRoleDao userRoleDao;

    public GetUserRoute(UserRoleDao userRoleDao) {
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
            if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, DBConstants.USER_ADD, null)) {
                    DDPInstance ddpInstance = DDPInstance.getDDPInstanceByRealmOrGuid(realm);
                    return userRoleDao.getAllUsersWithRoleForRealm(ddpInstance.getStudyGuid());
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

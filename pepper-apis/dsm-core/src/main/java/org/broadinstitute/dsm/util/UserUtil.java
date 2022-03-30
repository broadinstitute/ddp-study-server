package org.broadinstitute.dsm.util;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.roles.UserRoleDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;

public class UserUtil {

    public static final String USER_ID = "userId";
    private static final Logger logger = LoggerFactory.getLogger(UserUtil.class);

    public static String getUserId(Request request) {
        QueryParamsMap queryParams = request.queryMap();
        String userId = "";
        if (queryParams.value(USER_ID) != null) {
            userId = queryParams.get(USER_ID).value();
        }

        if (StringUtils.isBlank(userId)) {
            logger.warn("No userId query param was sent");
        }
        return userId;
    }

    public static boolean checkUserAccess(String realm, String userId, String role, String userIdRequest) {
        if (StringUtils.isNotBlank(userIdRequest) && !userId.equals(userIdRequest)) {
            throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
        }
        List<String> roles;
        if (StringUtils.isBlank(realm)) {
            roles = new UserRoleDao().checkUserAccess(Long.parseLong(userId));
        } else {
            roles = new UserRoleDao().getUserRolesPeRealm(Long.parseLong(userId), realm);
        }
        if (roles != null && !roles.isEmpty()) {
            return roles.contains(role);
        }
        return false;
    }


}

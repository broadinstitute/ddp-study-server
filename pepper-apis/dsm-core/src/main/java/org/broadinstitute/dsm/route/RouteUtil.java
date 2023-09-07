package org.broadinstitute.dsm.route;

import liquibase.pro.packaged.D;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.RoutePath;
import spark.QueryParamsMap;
import spark.Request;

/**
 * Collection of helper methods for route processing
 */
public class RouteUtil {

    public static String requireRealm(Request request) {
        QueryParamsMap queryParams = request.queryMap();
        if (!queryParams.hasKey(RoutePath.REALM)) {
            throw new DSMBadRequestException("Request must include realm parameter");
        }
        return requireRealm(queryParams.value(RoutePath.REALM));
    }

    public static String requireRealm(String realm) {
        if (StringUtils.isEmpty(realm)) {
            throw new DSMBadRequestException("Invalid realm parameter: blank");
        }
        return realm;
    }

    public static String getUserEmail(String userId) {
        try {
            UserDto user = new UserDao().get(Integer.parseInt(userId)).orElseThrow();
            return user.getEmailOrThrow();
        } catch (Exception e) {
            throw new DsmInternalError("Error getting email address for user " + userId, e);
        }
    }
}

package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import spark.QueryParamsMap;
import spark.Request;

/**
 * Collection of helper methods for route processing
 */
public class RouteUtil {

    public static String requireParam(Request request, String param) {
        QueryParamsMap queryParams = request.queryMap();
        String val = queryParams.value(param);
        if (StringUtils.isBlank(val)) {
            throw new DSMBadRequestException(String.format("Request must include %s parameter", param));
        }
        return val;
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

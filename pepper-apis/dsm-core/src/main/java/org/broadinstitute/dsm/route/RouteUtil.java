package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
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
}

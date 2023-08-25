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

    public static String getRealm(Request request) {
        QueryParamsMap queryParams = request.queryMap();
        if (!queryParams.hasKey(RoutePath.REALM)) {
            throw new DSMBadRequestException("Request must include realm parameter");
        }
        String realm = queryParams.value(RoutePath.REALM);
        if (StringUtils.isEmpty(realm)) {
            throw new DSMBadRequestException("Invalid realm parameter: blank");
        }
        return realm;
    }
}

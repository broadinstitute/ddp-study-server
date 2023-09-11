package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
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
        return requireParam(RoutePath.REALM, queryParams.value(RoutePath.REALM));
    }

    public static String requireParam(String paramName, String paramValue) {
        if (StringUtils.isEmpty(paramValue)) {
            throw new DSMBadRequestException("Missing request parameter: " + paramName);
        }
        return paramValue;
    }

    public static String requireRequestBody(Request request) {
        String payload = request.body();
        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Request must include a body/payload");
        }
        return payload;
    }

    public static String requireStringFromJsonObject(JsonObject jsonObject, String field) {
        String val = jsonObject.get(field).getAsString();
        if (StringUtils.isBlank(val)) {
            throw new DSMBadRequestException(String.format("Request body must include %s field", field));
        }
        return val;
    }

    public static void handleInvalidRouteMethod(Request request, String routeName) {
        throw new DsmInternalError(String.format("Invalid HTTP method for %s: %s", routeName, request.requestMethod()));
    }
}

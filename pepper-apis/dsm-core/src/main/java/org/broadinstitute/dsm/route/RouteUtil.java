package org.broadinstitute.dsm.route;

import com.google.gson.JsonObject;
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
        if (!queryParams.hasKey(param)) {
            throw new DSMBadRequestException("Request must include %s parameter".formatted(param));
        }
        return requireParam(param, queryParams.value(param));
    }

    public static String requireParam(String paramName, String paramValue) {
        if (StringUtils.isBlank(paramValue)) {
            throw new DSMBadRequestException("Missing request parameter: " + paramName);
        }
        return paramValue.trim();
    }

    public static String requireRequestBody(Request request) {
        String payload = request.body();
        if (StringUtils.isBlank(payload)) {
            throw new DSMBadRequestException("Request must include a body/payload");
        }
        return payload;
    }

    public static String requireStringFromJsonObject(JsonObject jsonObject, String field) {
        String val = jsonObject.get(field).getAsString().trim();
        if (StringUtils.isBlank(val)) {
            throw new DSMBadRequestException(String.format("Request body must include %s field", field));
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

    public static void handleInvalidRouteMethod(Request request, String routeName) {
        throw new DsmInternalError(String.format("Invalid HTTP method for %s: %s", routeName, request.requestMethod()));
    }
}

package org.broadinstitute.dsm.route;

import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.model.at.SearchKitRequest;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class KitSearchRoute extends RequestHandler {

    public static final String SEARCH_FIELD = "field";
    public static final String SEARCH_VALUE = "value";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String userIdRequest = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", userIdRequest) || UserUtil.checkUserAccess(null, userId,
                "kit_shipping_view", userIdRequest)) {
            QueryParamsMap queryParams = request.queryMap();
            String field = null;
            if (queryParams.value(SEARCH_FIELD) != null) {
                field = queryParams.get(SEARCH_FIELD).value();
            }
            String value = null;
            if (queryParams.value(SEARCH_VALUE) != null) {
                value = queryParams.get(SEARCH_VALUE).value();
            }
            String[] realms = null;
            if (queryParams.value(RoutePath.REALM) != null) {
                realms = queryParams.get(RoutePath.REALM).values();
            }
            if (StringUtils.isBlank(field) || StringUtils.isBlank(value)) {
                throw new RuntimeException("Information to perform a search was missing");
            }
            List<KitRequestShipping> kitRequestShipping = KitRequestShipping.findKitRequest(field, value, realms);
            if (Arrays.asList(realms).contains("atcp")) { //only if user has right to see atcp
                kitRequestShipping.addAll(SearchKitRequest.findATKitRequest(field, value));
            }
            return kitRequestShipping;
        } else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }
}

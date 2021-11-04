package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.KitRequestShipping;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class KitRequestRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(KitRequestRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        if (UserUtil.checkUserAccess(null, userId, "kit_shipping", null) || UserUtil.checkUserAccess(null, userId, "kit_shipping_view", null)) {
            logger.info("Getting list of kit requests");
            QueryParamsMap queryParams = request.queryMap();

            String realm = "";
            if (queryParams.value(RoutePath.REALM) != null) {
                realm = queryParams.get(RoutePath.REALM).value();
            }
            String target = "";
            if (queryParams.value(RequestParameter.QUERY_PARAM_TARGET) != null) {
                target = queryParams.get(RequestParameter.QUERY_PARAM_TARGET).value();
                if (StringUtils.isBlank(target)) {
                    throw new RuntimeException("Error - No target was sent");
                }
            }
            String kitType = "";
            if (queryParams.value(RoutePath.KIT_TYPE) != null) {
                kitType = queryParams.get(RoutePath.KIT_TYPE).value();
            }
            return KitRequestShipping.getKitRequestsByRealm(realm, target, kitType);
        }
        else {
            response.status(500);
            return new Result(500, UserErrorMessages.NO_RIGHTS);
        }
    }
}

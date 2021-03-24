package org.broadinstitute.dsm.route;

import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.AbstractionGroup;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.AbstractionUtil;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class AbstractionFormControlRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(AbstractionFormControlRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        else {
            throw new RuntimeException("No realm query param was sent");
        }

        if (StringUtils.isNotBlank(realm)) {
            if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, "mr_view")) {
                    return AbstractionUtil.getFormControls(realm);
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
            else if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, "mr_abstraction_admin")) {
                    String requestBody = request.body();
                    AbstractionGroup[] receivedAbstractionGroups = new GsonBuilder().create().fromJson(requestBody, AbstractionGroup[].class);
                    AbstractionGroup.saveFormControls(realm, receivedAbstractionGroups);
                    return new Result(200);
                }
                else {
                    response.status(500);
                    return new Result(500, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        logger.error("Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}

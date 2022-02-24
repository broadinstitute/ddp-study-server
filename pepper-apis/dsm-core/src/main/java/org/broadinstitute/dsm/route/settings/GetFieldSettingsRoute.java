package org.broadinstitute.dsm.route.settings;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class GetFieldSettingsRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(GetFieldSettingsRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isBlank(realm)) {
            throw new IllegalArgumentException("Realm cannot be empty");
        }

        String userIdRequest = UserUtil.getUserId(request);
        if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
            if (UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest) || UserUtil.checkUserAccess(realm, userId, "pt_list_view",
                    userIdRequest)) {
                return FieldSettings.getFieldSettings(realm);
            } else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS);
            }
        }
        logger.error("GetFieldSettingsRoute: Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}

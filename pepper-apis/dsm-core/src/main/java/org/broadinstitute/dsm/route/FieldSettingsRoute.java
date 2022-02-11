package org.broadinstitute.dsm.route;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.FieldSettings;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RequestParameter;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

public class FieldSettingsRoute extends RequestHandler {

    private static final String NO_RIGHTS_OF_CHANGES = "You don't have the right to modify settings";
    private static final Logger logger = LoggerFactory.getLogger(FieldSettingsRoute.class);
    private static final String NO_RIGHTS_OF_ACCESS = "You don't have the right to access the page";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        String userIdRequest = UserUtil.getUserId(request);
        if (StringUtils.isNotBlank(realm)) {
            if (RoutePath.RequestMethod.GET.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest) || UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdRequest)) {
                    return FieldSettings.getFieldSettings(realm);
                }
                else {
                    response.status(500);
                    return new Result(500, NO_RIGHTS_OF_ACCESS);
                }
            }
            if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, "field_settings", userIdRequest)) {
                    try {
                        String requestBody = request.body();
                        Type settingsType = new TypeToken<Map<String, Collection<FieldSettings>>>() {
                        }.getType();
                        Map<String, Collection<FieldSettings>> fieldSettingsLists = new Gson().fromJson(requestBody, settingsType);
                        FieldSettings.saveFieldSettings(realm, fieldSettingsLists, userId);
                        return new Result(200);
                    }
                    catch (Exception e) {
                        logger.error("Failed to save field setting ", e);
                        response.status(500);
                        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
                    }
                }
                else {
                    response.status(500);
                    return new Result(500, NO_RIGHTS_OF_CHANGES);
                }
            }
        }
        logger.error("FieldSettingsRoute: Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}

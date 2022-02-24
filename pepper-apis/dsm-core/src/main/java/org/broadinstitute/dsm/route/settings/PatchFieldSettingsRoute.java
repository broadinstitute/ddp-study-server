package org.broadinstitute.dsm.route.settings;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
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

public class PatchFieldSettingsRoute extends RequestHandler {

    private static final Logger logger = LoggerFactory.getLogger(PatchFieldSettingsRoute.class);

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        String realm = request.params(RequestParameter.REALM);
        if (StringUtils.isBlank(realm)) {
            throw new IllegalArgumentException("Realm cannot be empty");
        }

        String userIdRequest = UserUtil.getUserId(request);
        if (RoutePath.RequestMethod.PATCH.toString().equals(request.requestMethod())) {
            if (UserUtil.checkUserAccess(realm, userId, "field_settings", userIdRequest)) {
                try {
                    String requestBody = request.body();
                    Type settingsType = new TypeToken<Map<String, Collection<FieldSettings>>>() {
                    }.getType();
                    Map<String, Collection<FieldSettings>> fieldSettingsLists = new Gson().fromJson(requestBody, settingsType);
                    FieldSettings.saveFieldSettings(realm, fieldSettingsLists, userId);
                    return new Result(200);
                } catch (JsonSyntaxException e) {
                    logger.error("Failed to save field setting ", e);
                    response.status(500);
                    return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
                }
            } else {
                response.status(500);
                return new Result(500, UserErrorMessages.NO_RIGHTS_OF_CHANGES);
            }
        }
        logger.error("PatchFieldSettingsRoute: Request method not known");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }
}

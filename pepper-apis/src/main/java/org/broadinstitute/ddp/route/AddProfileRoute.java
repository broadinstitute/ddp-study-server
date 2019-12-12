package org.broadinstitute.ddp.route;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class AddProfileRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(AddProfileRoute.class);
    private final UserDao userDao;

    public AddProfileRoute(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String guid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Creating profile for {}", guid);

        JsonElement data = new JsonParser().parse(request.body());
        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
        }
        JsonObject payload = data.getAsJsonObject();

        String sexStr = null;
        if (payload.has(Profile.SEX) && !payload.get(Profile.SEX).isJsonNull()) {
            sexStr = payload.get(Profile.SEX).getAsString();
        }
        boolean isValidSex = userDao.isValidSex(sexStr);
        if (!isValidSex) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_SEX);
        }

        Profile profile = new Gson().fromJson(payload, Profile.class);
        TransactionWrapper.withTxn((Handle handle) -> {
            boolean isValidLanguage = userDao.isValidLanguage(profile.getPreferredLanguage(), handle);
            if (!isValidLanguage) {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_LANGUAGE_PREFERENCE);
            }
            boolean exists = userDao.doesProfileExist(handle, guid);
            if (!exists) {
                try {
                    userDao.addProfile(handle, profile, guid);
                } catch (RuntimeException e) {
                    throw new RuntimeException("error adding profile to the dao of user: " + guid, e);
                }
            } else {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.DUPLICATE_PROFILE);
            }
            handle.attach(DataExportDao.class).queueDataSync(guid);
            return null;
        });
        response.status(201);
        return profile;
    }
}

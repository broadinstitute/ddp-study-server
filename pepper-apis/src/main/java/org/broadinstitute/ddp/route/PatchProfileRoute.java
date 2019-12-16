package org.broadinstitute.ddp.route;

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

public class PatchProfileRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PatchProfileRoute.class);
    private final UserDao userDao;

    public PatchProfileRoute(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String guid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Updating profile information for {}", guid);

        JsonElement data = new JsonParser().parse(request.body());
        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
        }
        JsonObject payload = data.getAsJsonObject();

        String sexStr = null;
        if (payload.has(Profile.SEX) && !payload.get(Profile.SEX).isJsonNull()) {
            sexStr = payload.get(Profile.SEX).getAsString();
        }
        boolean isValidGender = userDao.isValidSex(sexStr);
        if (!isValidGender) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_SEX);
        }

        Profile modifiedProfile = TransactionWrapper.withTxn((Handle handle) -> {
            String language = null;
            if (payload.has(Profile.PREFERRED_LANGUAGE) && !payload.get(Profile.PREFERRED_LANGUAGE).isJsonNull()) {
                language = payload.get(Profile.PREFERRED_LANGUAGE).getAsString();
            }
            boolean isValidLanguage = userDao.isValidLanguage(language, handle);
            if (!isValidLanguage) {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_LANGUAGE_PREFERENCE);
            }

            boolean exist = userDao.doesProfileExist(handle, guid);
            Profile temp = null;
            if (exist) {
                temp = userDao.patchProfile(handle, payload, guid);
            } else {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_PROFILE);
            }
            handle.attach(DataExportDao.class).queueDataSync(guid);
            return temp;
        });
        response.status(200);
        return modifiedProfile;
    }
}


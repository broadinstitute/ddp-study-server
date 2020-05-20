package org.broadinstitute.ddp.route;

import java.time.DateTimeException;
import java.time.LocalDate;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class PatchProfileRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PatchProfileRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Updating profile information for user with guid {}", userGuid);

        JsonElement data = new JsonParser().parse(request.body());
        if (!data.isJsonObject() || data.getAsJsonObject().entrySet().size() == 0) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_BODY);
        }
        JsonObject json = data.getAsJsonObject();

        Profile payload;
        try {
            payload = new Gson().fromJson(json, Profile.class);
        } catch (Exception e) {
            LOG.warn("Error while parsing json", e);
            throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.BAD_PAYLOAD, "Error parsing payload"));
        }

        boolean providedSexStr = json.has(Profile.SEX);
        UserProfile.SexType sexType = providedSexStr ? parseSexType(response, payload) : null;

        boolean providedFirstName = json.has(Profile.FIRST_NAME);
        String firstName = providedFirstName ? payload.getFirstName() : null;

        boolean providedLastName = json.has(Profile.LAST_NAME);
        String lastName = providedLastName ? payload.getLastName() : null;

        boolean providedBirthDate = json.has(Profile.BIRTH_DATE);
        String birthDate = payload.getBirthDate();

        Profile modifiedProfile = TransactionWrapper.withTxn((Handle handle) -> {
            boolean providedLanguage = json.has(Profile.PREFERRED_LANGUAGE);
            Long languageId = providedLanguage ? parseLanguage(response, handle, payload) : null;

            var profileDao = handle.attach(UserProfileDao.class);
            UserProfile profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
            if (profile == null) {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_PROFILE);
                return null;
            }

            // Patch the existing profile with only things that were provided in payload.
            var builder = new UserProfile.Builder(profile);
            if (providedFirstName) {
                builder.setFirstName(firstName);
            }
            if (providedLastName) {
                builder.setLastName(lastName);
            }
            if (providedSexStr) {
                builder.setSexType(sexType);
            }
            if (providedBirthDate) {
                builder.setBirthDate(birthDate != null ? LocalDate.parse(birthDate) : null);
            }
            if (providedLanguage) {
                builder.setPreferredLangId(languageId);
            }

            profile = profileDao.updateProfile(builder.build());
            handle.attach(DataExportDao.class).queueDataSync(userGuid);
            return new Profile(profile);    // Convert to json view.
        });

        response.status(200);
        return modifiedProfile;
    }

    private UserProfile.SexType parseSexType(Response response, Profile payload) {
        String sexStr = payload.getSex();
        if (sexStr == null) {
            return null;
        }
        try {
            return UserProfile.SexType.valueOf(sexStr);
        } catch (IllegalArgumentException e) {
            LOG.warn("Provided invalid profile sex type: {}", sexStr, e);
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_SEX);
            return null;
        }
    }

    private LocalDate parseBirthDate(Profile payload) {
        Integer year = payload.getBirthYear();
        Integer month = payload.getBirthMonth();
        Integer day = payload.getBirthDayInMonth();
        if (year == null && month == null && day == null) {
            return null;
        } else if (year != null && month != null && day != null) {
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                LOG.warn("Invalid birth date", e);
                throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.BAD_PAYLOAD, "Invalid birth date"));
            }
        } else {
            LOG.warn("Full birth date was not provided");
            throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.BAD_PAYLOAD, "Need to provide full birth date"));
        }
    }

    private Long parseLanguage(Response response, Handle handle, Profile payload) {
        String language = payload.getPreferredLanguage();
        if (language == null) {
            return null;
        }
        Long languageId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(language);
        if (languageId != null) {
            return languageId;
        } else {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.INVALID_LANGUAGE_PREFERENCE);
            return null;
        }
    }
}

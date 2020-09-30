package org.broadinstitute.ddp.route;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
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
            throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.MISSING_BODY, "Missing body in payload"));
        }
        JsonObject json = data.getAsJsonObject();

        Profile payload;
        try {
            payload = new Gson().fromJson(json, Profile.class);
        } catch (Exception e) {
            LOG.warn("Error while parsing json", e);
            throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, "Error parsing payload"));
        }

        boolean providedSexStr = json.has(Profile.SEX);
        UserProfile.SexType sexType = providedSexStr ? parseSexType(response, payload) : null;

        boolean providedFirstName = json.has(Profile.FIRST_NAME);
        String firstName = providedFirstName ? payload.getFirstName() : null;

        boolean providedLastName = json.has(Profile.LAST_NAME);
        String lastName = providedLastName ? payload.getLastName() : null;

        boolean providedBirthDate = json.has(Profile.BIRTH_DATE);
        String birthDate = payload.getBirthDate();

        boolean providedBirthDateElements = json.has(Profile.BIRTH_YEAR)
                && json.has(Profile.BIRTH_MONTH)
                && json.has(Profile.BIRTH_DAY_IN_MONTH);
        LocalDate parsedBirthDate = providedBirthDateElements ? parseBirthDate(payload) : null;

        boolean providedShouldSkipLanguagePopup = json.has(Profile.SHOULD_SKIP_LANGUAGE_POPUP);
        Boolean shouldSkipLanguagePopup = providedShouldSkipLanguagePopup ? payload.getShouldSkipLanguagePopup() : null;

        Profile modifiedProfile = TransactionWrapper.withTxn((Handle handle) -> {
            boolean providedLanguage = json.has(Profile.PREFERRED_LANGUAGE);
            LanguageDto languageDto = providedLanguage ? parseLanguage(payload) : null;

            var profileDao = handle.attach(UserProfileDao.class);
            UserProfile profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
            if (profile == null) {
                String errorMsg = "Profile not found for user with guid: " + userGuid;
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.MISSING_PROFILE, errorMsg));
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
                try {
                    builder.setBirthDate(birthDate != null ? LocalDate.parse(birthDate) : null);
                } catch (DateTimeParseException e) {
                    String errorMsg = "Provided birth date is not a valid date";
                    throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_DATE, errorMsg));
                }
            } else if (providedBirthDateElements) {
                builder.setBirthDate(parsedBirthDate);
            }

            if (providedLanguage) {
                builder.setPreferredLangId(languageDto == null ? null : languageDto.getId());
            }

            if (providedShouldSkipLanguagePopup) {
                builder.setSkipLanguagePopup(shouldSkipLanguagePopup);
            }

            profile = profileDao.updateProfile(builder.build());
            handle.attach(DataExportDao.class).queueDataSync(userGuid);

            if (providedLanguage) {
                String auth0UserId = handle.attach(UserDao.class)
                        .findUserByGuid(userGuid)
                        .map(User::getAuth0UserId)
                        .orElse(null);
                if (StringUtils.isNotBlank(auth0UserId)) {
                    LOG.info("User {} has auth0 account, proceeding to sync user_metadata", userGuid);
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put(User.METADATA_LANGUAGE, languageDto == null ? null : languageDto.getIsoCode());
                    var result = Auth0ManagementClient.forUser(handle, userGuid).updateUserMetadata(auth0UserId, metadata);
                    if (result.hasThrown() || result.hasError()) {
                        var e = result.hasThrown() ? result.getThrown() : result.getError();
                        LOG.error("Error while updating user_metadata for user {}, user's language may be out-of-sync", userGuid, e);
                    } else {
                        LOG.info("Updated user_metadata for user {}", userGuid);
                    }
                }
            }

            return new Profile(profile);    // Convert to json view.
        });

        response.status(HttpStatus.SC_OK);
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
            String errorMsg = "Provided invalid profile sex type: " + sexStr;
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_SEX, errorMsg));
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
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_DATE, "Invalid birth date"));
            }
        } else {
            LOG.warn("Full birth date was not provided");
            throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                    new ApiError(ErrorCodes.INVALID_DATE, "Need to provide full birth date"));
        }
    }

    private LanguageDto parseLanguage(Profile payload) {
        String language = payload.getPreferredLanguage();
        if (language == null) {
            return null;
        }
        LanguageDto languageDto  = LanguageStore.get(language);
        if (languageDto != null) {
            return languageDto;
        } else {
            throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                    new ApiError(ErrorCodes.INVALID_LANGUAGE_PREFERENCE, "Invalid preferred language"));
        }
    }
}

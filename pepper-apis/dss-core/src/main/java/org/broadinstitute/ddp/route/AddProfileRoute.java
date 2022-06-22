package org.broadinstitute.ddp.route;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
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
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

@Slf4j
public class AddProfileRoute extends ValidatedJsonInputRoute<Profile> {
    @Override
    public Object handle(Request request, Response response, Profile profile) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        log.info("Creating profile for user with guid {}", userGuid);

        UserProfile.SexType sex = null;
        if (profile.getSex() != null) {
            try {
                sex = UserProfile.SexType.valueOf(profile.getSex());
            } catch (IllegalArgumentException e) {
                log.warn("Provided invalid profile sex type: {}", profile.getSex(), e);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_SEX,
                        "Provided invalid profile sex type: " + profile.getSex()));
            }
        }
        UserProfile.SexType sexType = sex;

        TransactionWrapper.useTxn((Handle handle) -> {
            String langCode = profile.getPreferredLanguage();
            LanguageDto languageDto = LanguageStore.get(langCode);
            Long langId = languageDto != null ? languageDto.getId() : null;
            if (StringUtils.isNotBlank(langCode) && langId == null) {
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.INVALID_LANGUAGE_PREFERENCE, "Invalid preferred language"));
            }

            UserProfileDao profileDao = handle.attach(UserProfileDao.class);
            boolean exists = profileDao.findProfileByUserGuid(userGuid).isPresent();
            if (!exists) {
                long userId = handle.attach(UserDao.class)
                        .findUserByGuid(userGuid)
                        .map(User::getId)
                        .orElseThrow(() -> new DDPException("Could not find user with guid " + userGuid));
                try {
                    profileDao.createProfile(UserProfile.builder()
                                    .userId(userId)
                                    .firstName(profile.getFirstName())
                                    .lastName(profile.getLastName())
                                    .sexType(sexType)
                                    .birthDate(profile.getBirthDate() != null ? LocalDate.parse(profile.getBirthDate()) : null)
                                    .preferredLangId(langId)
                                    .preferredLangCode(null)
                                    .skipLanguagePopup(profile.getSkipLanguagePopup())
                                    .build());
                } catch (DateTimeParseException e) {
                    String errorMsg = "Provided birth date is not a valid date";
                    throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.INVALID_DATE, errorMsg));
                } catch (Exception e) {
                    throw new DDPException("Error adding profile for user with guid " + userGuid, e);
                }

                if (languageDto != null) {
                    /*
                     * Ensure proper behavior for auth0-less accounts
                     * #ddp7931
                     */
                    String auth0UserId = handle.attach(UserDao.class)
                            .findUserByGuid(userGuid)
                            .flatMap(User::getAuth0UserId)
                            .orElse(null);
                    if (StringUtils.isNotBlank(auth0UserId)) {
                        log.info("User {} has auth0 account, proceeding to sync user_metadata", userGuid);
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put(User.METADATA_LANGUAGE, languageDto.getIsoCode());
                        var result = Auth0ManagementClient.forUser(handle, userGuid).updateUserMetadata(auth0UserId, metadata);
                        if (result.hasThrown() || result.hasError()) {
                            var e = result.hasThrown() ? result.getThrown() : result.getError();
                            log.error("Error while updating user_metadata for user {}, user's language may be out-of-sync", userGuid, e);
                        } else {
                            log.info("Updated user_metadata for user {}", userGuid);
                        }
                    }
                }
            } else {
                String errorMsg = "Profile already exists for user with guid: " + userGuid;
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.DUPLICATE_PROFILE, errorMsg));
            }

            handle.attach(DataExportDao.class).queueDataSync(userGuid);
        });

        response.status(HttpStatus.SC_CREATED);
        return profile;
    }
}

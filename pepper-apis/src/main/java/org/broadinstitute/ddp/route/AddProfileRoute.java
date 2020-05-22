package org.broadinstitute.ddp.route;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class AddProfileRoute extends ValidatedJsonInputRoute<Profile> {

    private static final Logger LOG = LoggerFactory.getLogger(AddProfileRoute.class);

    @Override
    public Object handle(Request request, Response response, Profile profile) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Creating profile for user with guid {}", userGuid);

        if (profile == null) {
            throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.MISSING_BODY, "Missing body in payload"));
        }

        UserProfile.SexType sex = null;
        if (profile.getSex() != null) {
            try {
                sex = UserProfile.SexType.valueOf(profile.getSex());
            } catch (IllegalArgumentException e) {
                LOG.warn("Provided invalid profile sex type: {}", profile.getSex(), e);
                throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.INVALID_SEX,
                        "Provided invalid profile sex type: " + profile.getSex()));
            }
        }
        UserProfile.SexType sexType = sex;

        TransactionWrapper.useTxn((Handle handle) -> {
            String langCode = profile.getPreferredLanguage();
            Long langId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(langCode);
            if (StringUtils.isNotBlank(langCode) && langId == null) {
                throw ResponseUtil.haltError(400, new ApiError(ErrorCodes.INVALID_LANGUAGE_PREFERENCE, "Invalid preferred language"));
            }

            UserProfileDao profileDao = handle.attach(UserProfileDao.class);
            boolean exists = profileDao.findProfileByUserGuid(userGuid).isPresent();
            if (!exists) {
                long userId = handle.attach(UserDao.class)
                        .findUserByGuid(userGuid)
                        .map(User::getId)
                        .orElseThrow(() -> new DDPException("Could not find user with guid " + userGuid));
                try {
                    profileDao.createProfile(new UserProfile.Builder(userId)
                            .setFirstName(profile.getFirstName())
                            .setLastName(profile.getLastName())
                            .setSexType(sexType)
                            .setBirthDate(profile.getBirthDate() != null ? LocalDate.parse(profile.getBirthDate()) : null)
                            .setPreferredLangId(langId)
                            .build());
                } catch (DateTimeParseException e) {
                    String errorMsg = "Provided birth date is not a valid date";
                    throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.INVALID_DATE, errorMsg));
                } catch (Exception e) {
                    throw new DDPException("Error adding profile for user with guid " + userGuid, e);
                }
            } else {
                String errorMsg = "Profile already exists for user with guid: " + userGuid;
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.DUPLICATE_PROFILE, errorMsg));
            }

            handle.attach(DataExportDao.class).queueDataSync(userGuid);
        });

        response.status(201);
        return profile;
    }
}

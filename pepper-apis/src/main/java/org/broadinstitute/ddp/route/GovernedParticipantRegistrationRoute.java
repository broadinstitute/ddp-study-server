package org.broadinstitute.ddp.route;

import java.time.ZoneId;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.GovernedUserRegistrationPayload;
import org.broadinstitute.ddp.json.UserRegistrationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.DateTimeUtils;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class GovernedParticipantRegistrationRoute extends ValidatedJsonInputRoute<GovernedUserRegistrationPayload> {
    private static final Logger LOG = LoggerFactory.getLogger(GovernedParticipantRegistrationRoute.class);

    @Override
    public Object handle(Request request, Response response, GovernedUserRegistrationPayload payload) throws Exception {
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        //String auth0ClientId = payload.getAuth0ClientId();
        //String auth0UserId = payload.getAuth0UserId();
        //String auth0Domain = payload.getAuth0Domain();
        String studyGuid = payload.getStudyGuid();
        return TransactionWrapper.withTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            UserDao userDao = handle.attach(UserDao.class);
            Optional<Long> studyId = new JdbiUmbrellaStudyCached(handle).getIdByGuid(studyGuid);
            if (studyId.isEmpty()) {
                ApiError error = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Study " + studyGuid + " does not exist");
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }
            User operatorUser = userDao.findUserByGuid(operatorGuid)
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));
            long auth0ClientId = operatorUser.getCreatedByClientId();
            Governance governance = userGovernanceDao.createGovernedUserWithGuidAlias(auth0ClientId, operatorUser.getId());
            userGovernanceDao.grantGovernedStudy(governance.getId(), studyId.get());
            User governedUser = userDao.findUserById(governance.getGovernedUserId())
                    .orElseThrow(() -> new DDPException("Could not find governed user with id " + governance.getGovernedUserId()));
            LOG.info("Created governed user with guid {} and granted access to study {} for proxy {}",
                    governedUser.getGuid(), studyGuid, operatorUser.getGuid());

            initializeProfile(handle, governedUser, payload);

            GovernancePolicy policy = handle.attach(StudyGovernanceDao.class).findPolicyByStudyGuid(studyGuid).orElse(null);
            if (policy != null && !policy.getAgeOfMajorityRules().isEmpty()) {
                handle.attach(StudyGovernanceDao.class).addAgeUpCandidate(policy.getStudyId(), governedUser.getId());
                LOG.info("Added governed user {} as age-up candidate in study {}", governedUser.getGuid(), policy.getStudyGuid());
            }

            return new UserRegistrationResponse(governedUser.getGuid());
        });
    }

    private void initializeProfile(Handle handle, User user, GovernedUserRegistrationPayload payload) {
        var profileDao = handle.attach(UserProfileDao.class);

        String firstName = payload.getFirstName();
        firstName = StringUtils.isNotBlank(firstName) ? firstName.trim() : null;
        String lastName = payload.getLastName();
        lastName = StringUtils.isNotBlank(lastName) ? lastName.trim() : null;
        LanguageDto languageDto = I18nUtil.determineUserLanguage(handle, payload.getStudyGuid(), payload.getLanguageCode());
        long languageId = languageDto.getId();
        ZoneId timeZone = DateTimeUtils.parseUserTimeZone(payload.getTimeZone());
        if (timeZone == null) {
            LOG.info("No user timezone is provided");
        }
        UserProfile profile = new UserProfile.Builder(user.getId())
                .setFirstName(firstName)
                .setLastName(lastName)
                .setPreferredLangId(languageId)
                .setTimeZone(timeZone)
                .build();
        profileDao.createProfile(profile);
        LOG.info("Initialized user profile for user with guid {}", user.getGuid());
    }
}

package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.GovernedUserRegistrationPayload;
import org.broadinstitute.ddp.json.UserRegistrationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.DateTimeUtils;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

import java.time.ZoneId;


@Slf4j
@AllArgsConstructor
public class GovernedParticipantRegistrationRoute extends ValidatedJsonInputRoute<GovernedUserRegistrationPayload> {
    private final TaskPublisher taskPublisher;

    @Override
    public Object handle(Request request, Response response, GovernedUserRegistrationPayload payload) throws Exception {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();
        String client = ddpAuth.getClient();
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        return TransactionWrapper.withTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            UserDao userDao = handle.attach(UserDao.class);
            StudyDto study = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
            if (study == null) {
                ApiError error = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Study " + studyGuid + " does not exist");
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, error);
            }
            User operatorUser = userDao.findUserByGuid(operatorGuid)
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + operatorGuid));
            long auth0ClientId = handle.attach(JdbiClient.class)
                    .getClientIdByAuth0ClientIdAndAuth0TenantId(client, study.getAuth0TenantId())
                    .orElseThrow(() -> new DDPException("Could not determine clientId"));
            Governance governance = userGovernanceDao.createGovernedUserWithGuidAlias(auth0ClientId, operatorUser.getId());
            userGovernanceDao.grantGovernedStudy(governance.getId(), study.getId());
            User governedUser = userDao.findUserById(governance.getGovernedUserId())
                    .orElseThrow(() -> new DDPException("Could not find governed user with id " + governance.getGovernedUserId()));
            log.info("Created governed user with guid {} and granted access to study {} for proxy {}",
                    governedUser.getGuid(), studyGuid, operatorUser.getGuid());

            String preferredLanguageCode = payload.getLanguageCode();
            if (preferredLanguageCode == null) {
                log.info("setting operator: {} preferred language for governed user: {}", operatorUser.getHruid(), governedUser.getHruid());
                var profileDao = handle.attach(UserProfileDao.class);
                //try to get Operator/Proxy users preferred language code
                UserProfile userProfile = profileDao.findProfileByUserGuid(operatorUser.getGuid()).orElse(null);
                preferredLanguageCode = userProfile != null ? userProfile.getPreferredLangCode() : preferredLanguageCode;
                payload.setLanguageCode(preferredLanguageCode);
            }

            initializeProfile(handle, governedUser, studyGuid, payload);

            GovernancePolicy policy = handle.attach(StudyGovernanceDao.class).findPolicyByStudyGuid(studyGuid).orElse(null);
            if (policy != null && !policy.getAgeOfMajorityRules().isEmpty()) {
                handle.attach(StudyGovernanceDao.class).addAgeUpCandidate(policy.getStudyId(), governedUser.getId(), operatorUser.getId());
                log.info("Added governed user {} as age-up candidate in study {}", governedUser.getGuid(), policy.getStudyGuid());
            }

            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(governedUser.getGuid(), studyGuid, EnrollmentStatusType.REGISTERED);
            log.info("Registered user {} with status {} in study {}", governedUser.getGuid(), EnrollmentStatusType.REGISTERED, studyGuid);

            handle.attach(DataExportDao.class).queueDataSync(governedUser.getId());
            taskPublisher.publishTask(
                    TaskPubSubPublisher.TASK_PARTICIPANT_REGISTERED,
                    "", studyGuid, governedUser.getGuid());

            return new UserRegistrationResponse(governedUser.getGuid());
        });
    }

    private void initializeProfile(Handle handle, User user, String studyGuid, GovernedUserRegistrationPayload payload) {
        var profileDao = handle.attach(UserProfileDao.class);

        String firstName = payload.getFirstName();
        firstName = StringUtils.isNotBlank(firstName) ? firstName.trim() : null;
        String lastName = payload.getLastName();
        lastName = StringUtils.isNotBlank(lastName) ? lastName.trim() : null;
        LanguageDto languageDto = I18nUtil.determineUserLanguage(handle, studyGuid, payload.getLanguageCode());
        long languageId = languageDto.getId();
        ZoneId timeZone = DateTimeUtils.parseTimeZone(payload.getTimeZone());
        if (timeZone == null) {
            log.info("No user timezone is provided");
        }
        UserProfile profile = UserProfile.builder()
                .userId(user.getId())
                .firstName(firstName)
                .lastName(lastName)
                .preferredLangId(languageId)
                .preferredLangCode(null)
                .timeZone(timeZone)
                .build();
        profileDao.createProfile(profile);
        log.info("Initialized user profile for user with guid {}", user.getGuid());
    }
}

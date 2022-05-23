package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetrics;
import org.broadinstitute.ddp.analytics.GoogleAnalyticsMetricsTracker;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.*;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.LocalRegistrationResponse;
import org.broadinstitute.ddp.json.UserCreationPayload;
import org.broadinstitute.ddp.json.UserRegistrationPayload;
import org.broadinstitute.ddp.json.UserRegistrationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.util.*;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static spark.Spark.halt;

/**
 * Creates a new user. This route is expected to be locked
 * down via Auth0 IP ranges and shared secret header.
 * Irrespective of whether user exists or is new, the corresponding
 * ddp user id is returned so that auth0 can add it
 * as the {@link org.broadinstitute.ddp.constants.Auth0Constants#DDP_USER_ID_CLAIM}.
 */
@Slf4j
@AllArgsConstructor
public class UserCreationRoute extends ValidatedJsonInputRoute<UserCreationPayload> {
    private static final String MODE_LOGIN = "login";

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, UserCreationPayload payload) throws Exception {
        log.info("Attempting creation the user for study {}", payload.getStudyGuid());

        return TransactionWrapper.withTxn(handle -> {
            final var study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(payload.getStudyGuid());
            if (study == null) {
                throw new RuntimeException("Study " + payload.getStudyGuid() + " wasn't found");
            }


            final var operatorUser = handle.attach(UserDao.class).findUserByAuth0UserId(auth0UserId.get(), study.getAuth0TenantId()).orElse(null);

            var pair = signUpNewOperator(response, handle, study, auth0UserId.get(), payload, clientConfig, mgmtClient);

            return null;
        };
    }

    private User processInvitation(Response response, Handle handle, StudyDto study, InvitationDto invitation, String auth0UserId,
                                   UserRegistrationPayload payload, StudyClientConfiguration clientConfig,
                                   Auth0ManagementClient mgmtClient) {
        var invitationDao = handle.attach(InvitationDao.class);
        var userDao = handle.attach(UserDao.class);
        String invitationGuid = invitation.getInvitationGuid();
        String studyGuid = study.getGuid();

        if (invitation.getInvitationType() == InvitationType.AGE_UP) {
            var user = userDao.findUserById(invitation.getUserId())
                    .orElseThrow(() -> new DDPException("Could not find user " + invitation.getUserId()));
            if (user.hasAuth0Account()) {
                throw new DDPException("There is already an account-bearing user for invitation " + invitationGuid);
            }

            List<Governance> governances;
            try (Stream<Governance> governanceStream = handle.attach(UserGovernanceDao.class)
                    .findActiveGovernancesByParticipantAndStudyGuids(user.getGuid(), studyGuid)) {
                governances = governanceStream.collect(Collectors.toList());
            }

            String operatorGuid = (!governances.isEmpty())
                    ? governances.stream().findFirst().get().getProxyUserGuid()
                    : user.getGuid();

            // verify that there is governance policy configured for the study and that the
            // user has reached age of majority.
            GovernancePolicy policy = handle.attach(StudyGovernanceDao.class).findPolicyByStudyGuid(studyGuid).orElse(null);
            if (policy != null) {
                if (policy.hasReachedAgeOfMajority(handle, interpreter, user.getGuid(), operatorGuid)) {
                    log.info("Assigning {} to user {} for invitation {}", auth0UserId, user.getGuid(), invitationGuid);

                    var numRows = userDao.updateAuth0UserId(user.getGuid(), auth0UserId);
                    if (numRows != 1) {
                        throw new DDPException("Updated " + numRows + " for " + auth0UserId);
                    }
                    log.info("User {} has been associated with auth0 id {}", user.getGuid(), auth0UserId);

                    invitationDao.markAccepted(invitation.getInvitationId(), Instant.now());

                    EventSignal signal = new EventSignal(user.getId(), user.getId(), user.getGuid(), user.getGuid(),
                            study.getId(), study.getGuid(), EventTriggerType.GOVERNED_USER_REGISTERED);
                    EventService.getInstance().processAllActionsForEventSignal(handle, signal);
                } else {
                    log.error("User {} is not allowed to create an account yet because they have not reached age of majority "
                            + " in study {} with invitation {}", user.getGuid(), studyGuid, invitationGuid);
                    ResponseUtil.halt422ErrorResponse(response, ErrorCodes.GOVERNANCE_POLICY_VIOLATION);
                }
            } else {
                log.error("No governance policy for study {}.  Why is a client registering user {} with invitation {} ?",
                        studyGuid, auth0UserId, invitationGuid);
                ResponseUtil.halt422ErrorResponse(response, ErrorCodes.GOVERNANCE_POLICY_VIOLATION);
            }

            return user;
        } else if (invitation.getInvitationType() == InvitationType.RECRUITMENT) {
            var pair = signUpNewOperator(response, handle, study, auth0UserId, payload, clientConfig, mgmtClient);
            invitationDao.assignAcceptingUser(invitation.getInvitationId(), pair.getParticipantUser().getId(), Instant.now());
            log.info("Assigned invitation {} of type {} to participant user {}", invitation.getInvitationGuid(),
                    invitation.getInvitationType(), pair.getParticipantUser().getGuid());
            triggerUserRegisteredEvents(handle, study, pair.getOperatorUser(), pair.getParticipantUser());
            publishRegisteredPubSubMessage(studyGuid, pair.getParticipantUser().getGuid());
            return pair.getOperatorUser();
        } else {
            throw new DDPException("Unhandled invitation type " + invitation.getInvitationType());
        }
    }

    private UserPair signUpNewOperator(Response response, Handle handle, StudyDto study, String auth0UserId,
                                       UserRegistrationPayload payload, StudyClientConfiguration clientConfig,
                                       Auth0ManagementClient mgmtClient) {
        String studyGuid = study.getGuid();
        log.info("Attempting to register new user {}, with client {}  study {}  {}",
                auth0UserId, clientConfig.getAuth0ClientId(), studyGuid,
                payload.getTempUserGuid() != null ? " (temp user " + payload.getTempUserGuid() + ")" : " NO-Temp-User");

        User operatorUser = registerUser(response, payload, handle,
                clientConfig.getAuth0Domain(), clientConfig.getAuth0ClientId(), auth0UserId);

        GovernancePolicy policy = handle.attach(StudyGovernanceDao.class).findPolicyByStudyGuid(studyGuid).orElse(null);
        User participantUser;
        if (policy == null) {
            log.info("No study governance policy found, continuing with operator user as the study user");
            participantUser = operatorUser;
        } else {
            participantUser = handleGovernancePolicy(response, payload, handle, policy, operatorUser, clientConfig);
        }

        registerUserWithStudy(handle, study, participantUser);
        handle.attach(DataExportDao.class).queueDataSync(participantUser.getId());

        unregisterEmailFromStudyMailingList(handle, study, operatorUser, mgmtClient);
        return new UserPair(operatorUser, participantUser);
    }


    private String handleExistingUser(Response response, UserRegistrationPayload payload, Handle handle, StudyDto study, User user,
                                      Auth0ManagementClient mgmtClient) {
        String tempUserGuid = payload.getTempUserGuid();
        if (tempUserGuid != null) {
            String msg = String.format("Using existing user to upgrade temporary user with guid '%s' is not supported", tempUserGuid);
            log.warn(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(ErrorCodes.NOT_SUPPORTED, msg));
        }

        EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(user.getGuid(), study.getGuid())
                .orElse(null);

        if (status == null) {
            // Find if user is a proxy of any other user in the study, whether active or not.
            long numGovernances;
            try (Stream<Governance> govStream = handle.attach(UserGovernanceDao.class)
                    .findGovernancesByProxyAndStudyGuids(user.getGuid(), study.getGuid())) {
                numGovernances = govStream.count();
            }
            if (numGovernances > 0) {
                log.info("Existing user {} is a proxy of {} users in study {}", user.getGuid(), numGovernances, study.getGuid());
                return user.getGuid();
            }

            boolean isStudyAdmin = handle.attach(AuthDao.class)
                    .findAdminAccessibleStudyGuids(user.getGuid())
                    .contains(study.getGuid());
            if (isStudyAdmin) {
                log.info("Existing user {} is an admin in study {}", user.getGuid(), study.getGuid());
                return user.getGuid();
            }

            if (MODE_LOGIN.equals(payload.getMode())) {
                String msg = String.format("User needs to register with study '%s' before logging in", study.getGuid());
                log.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(ErrorCodes.SIGNUP_REQUIRED, msg));
            } else {
                registerUserWithStudy(handle, study, user);
                triggerUserRegisteredEvents(handle, study, user, user);
                handle.attach(DataExportDao.class).queueDataSync(user.getId());
                unregisterEmailFromStudyMailingList(handle, study, user, mgmtClient);
                return user.getGuid();
            }
        } else {
            log.info("Existing user {} is in study {} with status {}", user.getGuid(), study.getGuid(), status);
            GoogleAnalyticsMetricsTracker.getInstance().sendAnalyticsMetrics(
                    study.getGuid(), GoogleAnalyticsMetrics.EVENT_CATEGORY_USER_LOGIN,
                    GoogleAnalyticsMetrics.EVENT_ACTION_USER_LOGIN, GoogleAnalyticsMetrics.EVENT_LABEL_USER_LOGIN,
                    null, 1);
            return user.getGuid();
        }
    }

    private User registerUser(Response response, UserRegistrationPayload payload, Handle handle,
                              String auth0Domain, String auth0ClientId, String auth0UserId) {
        User user;
        UserDao userDao = handle.attach(UserDao.class);
        if (payload.getTempUserGuid() != null) {
            User tempUser = validateTemporaryUser(response, userDao, payload.getTempUserGuid());
            user = upgradeTemporaryUser(response, userDao, tempUser, auth0UserId);
        } else {
            user = userDao.createUser(auth0Domain, auth0ClientId, auth0UserId);
            log.info("Registered user {} with client {}", auth0UserId, auth0ClientId);
        }
        initializeProfile(handle, user, payload);
        return user;
    }

    private User handleGovernancePolicy(Response response, UserRegistrationPayload payload, Handle handle,
                                        GovernancePolicy policy, User operatorUser, StudyClientConfiguration clientConfig) {
        boolean shouldCreateGoverned;
        try {
            shouldCreateGoverned = policy.shouldCreateGovernedUser(handle, interpreter, operatorUser.getGuid());
        } catch (Exception e) {
            String msg = "Error while evaluating study governance policy for study " + policy.getStudyGuid();
            log.error(msg, e);
            throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.SERVER_ERROR, msg));
        }
        if (!shouldCreateGoverned) {
            log.info("Governance policy evaluated and no governed user will be created");
            return operatorUser;
        }

        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        Governance gov = userGovernanceDao.createGovernedUserWithGuidAlias(clientConfig.getClientId(), operatorUser.getId());
        userGovernanceDao.grantGovernedStudy(gov.getId(), policy.getStudyId());
        log.info("Created governed user with guid {} and granted access to study {} for proxy {}",
                gov.getGovernedUserGuid(), policy.getStudyGuid(), operatorUser.getGuid());

        User governedUser = handle.attach(UserDao.class).findUserById(gov.getGovernedUserId())
                .orElseThrow(() -> new DDPException("Could not find governed user with id " + gov.getGovernedUserId()));
        initializeProfile(handle, governedUser, payload);

        int numInstancesReassigned = handle.attach(ActivityInstanceDao.class)
                .reassignInstancesInStudy(policy.getStudyId(), operatorUser.getId(), gov.getGovernedUserId());
        log.info("Re-assigned {} activity instances in study {} from operator {} to governed user {}",
                numInstancesReassigned, policy.getStudyGuid(), operatorUser.getGuid(), gov.getGovernedUserGuid());

        int numEventsReassigned = handle.attach(QueuedEventDao.class)
                .reassignQueuedEventsInStudy(policy.getStudyId(), operatorUser.getId(), gov.getGovernedUserId());
        log.info("Re-assigned {} queued events in study {} from operator {} to governed user {}",
                numEventsReassigned, policy.getStudyGuid(), operatorUser.getGuid(), gov.getGovernedUserGuid());

        if (!policy.getAgeOfMajorityRules().isEmpty()) {
            handle.attach(StudyGovernanceDao.class).addAgeUpCandidate(policy.getStudyId(), governedUser.getId(), operatorUser.getId());
            log.info("Added governed user {} as age-up candidate in study {}", governedUser.getGuid(), policy.getStudyGuid());
        }

        return governedUser;
    }

    private void registerUserWithStudy(Handle handle, StudyDto study, User user) {
        JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
        EnrollmentStatusType status = jdbiEnrollment
                .getEnrollmentStatusByUserAndStudyGuids(user.getGuid(), study.getGuid())
                .orElse(null);
        if (status == null) {
            EnrollmentStatusType initialStatus = EnrollmentStatusType.REGISTERED;
            jdbiEnrollment.changeUserStudyEnrollmentStatus(user.getGuid(), study.getGuid(), initialStatus);
            log.info("Registered user {} with status {} in study {}", user.getGuid(), initialStatus, study.getGuid());

            //send GA events
            GoogleAnalyticsMetricsTracker.getInstance().sendAnalyticsMetrics(
                    study.getGuid(), GoogleAnalyticsMetrics.EVENT_CATEGORY_USER_REGISTRATION,
                    GoogleAnalyticsMetrics.EVENT_ACTION_USER_REGISTRATION, GoogleAnalyticsMetrics.EVENT_LABEL_USER_REGISTRATION,
                    null, 1);

            GoogleAnalyticsMetricsTracker.getInstance().sendAnalyticsMetrics(
                    study.getGuid(), GoogleAnalyticsMetrics.EVENT_CATEGORY_USER_LOGIN,
                    GoogleAnalyticsMetrics.EVENT_ACTION_USER_LOGIN, GoogleAnalyticsMetrics.EVENT_LABEL_USER_LOGIN,
                    null, 1);
        } else {
            log.warn("User {} is already registered in study {} with status {}", user.getGuid(), study.getGuid(), status);
        }
    }

    private void initializeProfile(Handle handle, User user, UserRegistrationPayload payload) {
        var profileDao = handle.attach(UserProfileDao.class);
        UserProfile profile = profileDao.findProfileByUserId(user.getId()).orElse(null);

        // If any name part is blank, then don't use it.
        String firstName = payload.getFirstName();
        firstName = StringUtils.isNotBlank(firstName) ? firstName.trim() : null;
        String lastName = payload.getLastName();
        lastName = StringUtils.isNotBlank(lastName) ? lastName.trim() : null;
        LanguageDto languageDto = I18nUtil.determineUserLanguage(handle, payload.getStudyGuid(), payload.getLanguageCode());
        long languageId = languageDto.getId();
        ZoneId timeZone = DateTimeUtils.parseTimeZone(payload.getTimeZone());
        if (timeZone == null) {
            log.info("No user timezone is provided");
        }

        if (profile == null) {
            profile = new UserProfile.Builder(user.getId())
                    .setFirstName(firstName)
                    .setLastName(lastName)
                    .setPreferredLangId(languageId)
                    .setTimeZone(timeZone)
                    .build();
            profileDao.createProfile(profile);
            log.info("Initialized user profile for user with guid {}", user.getGuid());
        } else {
            boolean shouldUpdate = false;
            var updated = new UserProfile.Builder(profile);

            if (profile.getFirstName() == null) {
                updated.setFirstName(firstName);
                shouldUpdate = true;
            }
            if (profile.getLastName() == null) {
                updated.setLastName(lastName);
                shouldUpdate = true;
            }
            if (profile.getPreferredLangId() == null) {
                updated.setPreferredLangId(languageId);
                shouldUpdate = true;
            }
            if (profile.getTimeZone() == null) {
                updated.setTimeZone(timeZone);
                shouldUpdate = true;
            }

            if (shouldUpdate) {
                profileDao.updateProfile(updated.build());
                log.info("Updated user profile for user with guid {}", user.getGuid());
            }
        }

        String auth0UserId = user.getAuth0UserId();
        if (StringUtils.isNotBlank(auth0UserId)) {
            log.info("User {} has auth0 account, proceeding to sync user_metadata", user.getGuid());
            Map<String, Object> metadata = new HashMap<>();
            metadata.put(User.METADATA_LANGUAGE, languageDto.getIsoCode());
            var result = Auth0ManagementClient.forUser(handle, user.getGuid()).updateUserMetadata(auth0UserId, metadata);
            if (result.hasThrown() || result.hasError()) {
                var e = result.hasThrown() ? result.getThrown() : result.getError();
                log.error("Error while updating user_metadata for user {}, user's language may be out-of-sync", user.getGuid(), e);
            } else {
                log.info("Updated user_metadata for user {}", user.getGuid());
            }
        }
    }

    private void triggerUserRegisteredEvents(Handle handle, StudyDto studyDto, User operator, User participant) {
        var signal = new EventSignal(
                operator.getId(),
                participant.getId(),
                participant.getGuid(),
                operator.getGuid(),
                studyDto.getId(),
                studyDto.getGuid(),
                EventTriggerType.USER_REGISTERED);
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }

    @Value
    @AllArgsConstructor
    private static class UserPair {
        User operatorUser;
        User participantUser;
    }
}

package org.broadinstitute.ddp.route;

import static spark.Spark.halt;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.LocalRegistrationResponse;
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
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.DateTimeUtils;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

/**
 * Creates a new user. This route is expected to be locked
 * down via Auth0 IP ranges and shared secret header.
 * Irrespective of whether user exists or is new, the corresponding
 * ddp user id is returned so that auth0 can add it
 * as the {@link org.broadinstitute.ddp.constants.Auth0Constants#DDP_USER_ID_CLAIM}.
 */
@Slf4j
@AllArgsConstructor
public class UserRegistrationRoute extends ValidatedJsonInputRoute<UserRegistrationPayload> {
    private static final String MODE_LOGIN = "login";

    private final PexInterpreter interpreter;
    private final TaskPublisher taskPublisher;

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, UserRegistrationPayload payload) throws Exception {
        checkRequestPayload(response, payload);

        var doLocalRegistration = payload.isLocalRegistration();
        String auth0ClientId = payload.getAuth0ClientId();
        String studyGuid = payload.getStudyGuid();
        String invitationGuid = payload.getInvitationGuid();
        final var auth0UserId = new AtomicReference<String>();
        AtomicReference<String> ddpUserGuid = new AtomicReference<>();

        log.info("Attempting registration with client {}, study {} and invitation {}", auth0ClientId, studyGuid, invitationGuid);

        return TransactionWrapper.withTxn(handle -> {
            auth0UserId.set(payload.getAuth0UserId());
            String auth0Domain = payload.getAuth0Domain();

            StudyDto study;
            if (doLocalRegistration && StringUtils.isBlank(auth0Domain)) {
                study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            } else {
                study = handle.attach(JdbiUmbrellaStudy.class).findByDomainAndStudyGuid(auth0Domain, studyGuid);
            }
            if (study == null) {
                ApiError err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                log.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            if (doLocalRegistration && StringUtils.isBlank(auth0Domain)) {
                auth0Domain = handle.attach(JdbiAuth0Tenant.class).findById(study.getAuth0TenantId())
                        .map(Auth0TenantDto::getDomain)
                        .orElse(null);
                log.info("Using auth0 domain {} for local registration", auth0Domain);
            }

            StudyClientConfiguration clientConfig = handle.attach(ClientDao.class).getConfiguration(auth0ClientId, auth0Domain);
            if (clientConfig == null) {
                log.warn("Attempted to register user {} using Auth0 client {} that is revoked or not found", auth0UserId, auth0ClientId);
                throw halt(HttpStatus.SC_UNAUTHORIZED);
            }

            Auth0Util auth0Util = new Auth0Util(auth0Domain);
            var mgmtClient = Auth0Util.getManagementClientForDomain(handle, auth0Domain);

            String auth0ClientSecret = clientConfig.getAuth0SigningSecret();
            Auth0Util.RefreshTokenResponse auth0RefreshResponse = null;
            if (doLocalRegistration) {
                auth0RefreshResponse = auth0Util.getRefreshTokenFromCode(payload.getAuth0Code(), auth0ClientId,
                        auth0ClientSecret, payload.getRedirectUri());
                auth0UserId.set(Auth0Util.getVerifiedAuth0UserId(auth0RefreshResponse.getIdToken(), auth0Domain));
                log.info("Successfully exchanged auth0 code for auth0UserId {} for local registration", auth0UserId);

                // Look for potential user metadata.
                var getResult = mgmtClient.getAuth0User(auth0UserId.get());
                if (getResult.hasFailure()) {
                    throw getResult.hasThrown() ? getResult.getThrown() : getResult.getError();
                }
                var auth0User = getResult.getBody();
                if (auth0User.getUserMetadata() != null) {
                    if (payload.getFirstName() == null) {
                        Object value = auth0User.getUserMetadata().get(User.METADATA_FIRST_NAME);
                        payload.setFirstName(value == null ? null : (String) value);
                    }
                    if (payload.getLastName() == null) {
                        Object value = auth0User.getUserMetadata().get(User.METADATA_LAST_NAME);
                        payload.setLastName(value == null ? null : (String) value);
                    }
                }
            }

            var userDao = handle.attach(UserDao.class);
            User operatorUser = userDao.findUserByAuth0UserId(auth0UserId.get(), study.getAuth0TenantId()).orElse(null);

            if (StringUtils.isNotBlank(invitationGuid) && operatorUser == null) {
                var invitationDao = handle.attach(InvitationDao.class);
                InvitationDto invitation = invitationDao.findByInvitationGuid(study.getId(), invitationGuid)
                        .orElseThrow(() -> new DDPException("Could not find invitation "
                                + invitationGuid + " for user " + auth0UserId));

                if (invitation.isVoid()) {
                    throw new DDPException("Invitation " + invitationGuid + " for user " + auth0UserId + "is voided");
                } else if (invitation.isAccepted()) {
                    throw new DDPException("Invitation " + invitationGuid + " has already been accepted");
                }

                operatorUser = processInvitation(response, handle, study, invitation, auth0UserId.get(),
                        payload, clientConfig, mgmtClient);
                ddpUserGuid.set(operatorUser.getGuid());
            } else if (operatorUser == null) {
                var pair = signUpNewOperator(response, handle, study, auth0UserId.get(),
                        payload, clientConfig, mgmtClient);
                operatorUser = pair.getOperatorUser();
                if (!payload.skipTriggerEvents()) {
                    triggerUserRegisteredEvents(handle, study, operatorUser, pair.getParticipantUser());
                    publishRegisteredPubSubMessage(studyGuid, pair.getParticipantUser().getGuid());
                } else {
                    log.info("---skipping triggering events and pubsub---");
                }
                ddpUserGuid.set(operatorUser.getGuid());
            } else {
                log.info("Attempting to register existing user {} with client {} and study {}", auth0UserId, auth0ClientId, studyGuid);
                ddpUserGuid.set(handleExistingUser(response, payload, handle, study, operatorUser, mgmtClient));
            }

            if (doLocalRegistration) {
                return saveDDPGuidInAuth0Metadata(mgmtClient, ddpUserGuid.get(), auth0UserId.get(),
                        auth0ClientId, auth0ClientSecret, auth0RefreshResponse.getRefreshToken());
            } else {
                return new UserRegistrationResponse(ddpUserGuid.get());
            }
        });
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

    private void checkRequestPayload(Response response, UserRegistrationPayload payload) {
        StringBuilder sb = new StringBuilder();

        if (!payload.isLocalRegistration()) {
            if (StringUtils.isBlank(payload.getAuth0Domain())) {
                sb.append("Property 'auth0Domain' is required\n");
            }
            if (StringUtils.isBlank(payload.getAuth0UserId())) {
                sb.append("Property 'auth0UserId' is required\n");
            }
        }

        String msg = sb.toString().trim();
        if (StringUtils.isNotBlank(msg)) {
            ApiError err = new ApiError(ErrorCodes.BAD_PAYLOAD, msg);
            log.warn("Missing properties in payload: {}", err.getMessage());
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
        }

        if (payload.getTimeZone() != null) {
            if (!ZoneId.getAvailableZoneIds().contains(payload.getTimeZone())) {
                ApiError err = new ApiError(ErrorCodes.BAD_PAYLOAD, String.format(
                        "Provided timezone '%s' is not a recognized region id", payload.getTimeZone()));
                log.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
            }
        }
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

        } else {
            log.warn("User {} is already registered in study {} with status {}", user.getGuid(), study.getGuid(), status);
        }
    }

    private void unregisterEmailFromStudyMailingList(Handle handle, StudyDto study, User user, Auth0ManagementClient mgmtClient) {
        String userEmail = null;

        /*
         * make sure users which do not have an auth0 identifier are handled.
         * Returning may be fine here, but since the non-auth0 accounts may still have an
         * email address, there is some extra work do to.
         * #ddp7931
         */
        if (user.hasAuth0Account() == false) {
            throw new NotImplementedException("handle users which do not have an auth0 account safely");
        }

        var auth0UserId = user.getAuth0UserId().get();

        var getResult = mgmtClient.getAuth0User(auth0UserId);
        if (getResult.hasFailure()) {
            var e = getResult.hasThrown() ? getResult.getThrown() : getResult.getError();
            log.error("Auth0 request to retrieve auth0 user {} failed", auth0UserId, e);
        } else {
            userEmail = getResult.getBody().getEmail();
        }
        
        if (StringUtils.isNotBlank(userEmail)) {
            int numDeleted = handle.attach(JdbiMailingList.class).deleteByEmailAndStudyId(userEmail, study.getId());
            if (numDeleted == 1) {
                log.info("Removed user {} from study {} mailing list", auth0UserId, study.getGuid());
            } else if (numDeleted > 1) {
                log.warn("Removed {} mailing list entries for user {} and study {}", numDeleted, auth0UserId, study.getGuid());
            }
        } else {
            log.error("No email for user {} to remove them from mailing list of study {}", auth0UserId, study.getGuid());
        }
    }

    /**
     * Saves the user's ddp guid in auth0's user app metadata
     * so that different clients can keep track of different ddp
     * user ids for the same auth0 user id.  Returns the object
     * that should be sent to the client.
     */
    private LocalRegistrationResponse saveDDPGuidInAuth0Metadata(Auth0ManagementClient mgmtClient, String ddpUserGuid,
                                                                 String auth0UserId, String auth0ClientId,
                                                                 String auth0ClientSecret, String refreshToken) {
        // set the ddp user guid in the user's app metadata, keyed by client id so that
        // different deployments can maintain different generated guids
        log.info("Setting auth0 user's metadata so that auth0 user {} has ddp user guid {} for client {}",
                auth0UserId, ddpUserGuid, auth0ClientId);
        mgmtClient.setUserGuidForAuth0User(auth0UserId, auth0ClientId, ddpUserGuid);
        Auth0Util auth0Util = new Auth0Util(mgmtClient.getDomain());
        return new LocalRegistrationResponse(auth0Util.refreshToken(auth0ClientId, auth0ClientSecret, refreshToken));
    }

    private User validateTemporaryUser(Response response, UserDao userDao, String tempUserGuid) {
        User tempUser = userDao.findUserByGuid(tempUserGuid).orElse(null);

        if (tempUser == null) {
            String msg = String.format("Could not find temporary user with guid '%s'", tempUserGuid);
            log.warn(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.USER_NOT_FOUND, msg));
        } else if (!tempUser.isTemporary()) {
            String msg = String.format("User with guid '%s' is not a temporary user", tempUserGuid);
            log.warn(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(ErrorCodes.NOT_SUPPORTED, msg));
        } else if (tempUser.isExpired()) {
            String msg = String.format("Temporary user with guid '%s' has already expired", tempUserGuid);
            log.warn(msg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, new ApiError(ErrorCodes.EXPIRED, msg));
        }

        return tempUser;
    }

    private User upgradeTemporaryUser(Response response, UserDao userDao, User tempUser, String auth0UserId) {
        String tempUserGuid = tempUser.getGuid();
        try {
            userDao.upgradeUserToPermanentById(tempUser.getId(), auth0UserId);
            log.info("Upgraded temporary user with guid '{}'", tempUserGuid);
        } catch (Exception e) {
            String msg = String.format("Error while upgrading temporary user with guid '%s'", tempUserGuid);
            log.error(msg, e);
            throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.SERVER_ERROR, msg));
        }
        return userDao.findUserByGuid(tempUserGuid).orElseThrow(() -> new DDPException("Could not find user with guid " + tempUserGuid));
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
            profile = UserProfile.builder()
                    .userId(user.getId())
                    .firstName(firstName)
                    .lastName(lastName)
                    .preferredLangId(languageId)
                    .preferredLangCode(null)
                    .timeZone(timeZone)
                    .build();
            profileDao.createProfile(profile);
            log.info("Initialized user profile for user with guid {}", user.getGuid());
        } else {
            boolean shouldUpdate = false;
            var updated = new UserProfile(profile).toBuilder();

            if (profile.getFirstName() == null) {
                updated.firstName(firstName);
                shouldUpdate = true;
            }
            if (profile.getLastName() == null) {
                updated.lastName(lastName);
                shouldUpdate = true;
            }
            if (profile.getPreferredLangId() == null) {
                updated.preferredLangId(languageId);
                updated.preferredLangCode(null);
                shouldUpdate = true;
            }
            if (profile.getTimeZone() == null) {
                updated.timeZone(timeZone);
                shouldUpdate = true;
            }

            if (shouldUpdate) {
                profileDao.updateProfile(updated.build());
                log.info("Updated user profile for user with guid {}", user.getGuid());
            }
        }

        var auth0UserId = user.getAuth0UserId().orElse(StringUtils.EMPTY);
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

    private void publishRegisteredPubSubMessage(String studyGuid, String participantGuid) {
        String payload = ""; // No payload.
        taskPublisher.publishTask(
                TaskPubSubPublisher.TASK_PARTICIPANT_REGISTERED,
                payload, studyGuid, participantGuid);
    }

    private static class UserPair {
        private final User operatorUser;
        private final User participantUser;

        public UserPair(User operatorUser, User participantUser) {
            this.operatorUser = operatorUser;
            this.participantUser = participantUser;
        }

        public User getOperatorUser() {
            return operatorUser;
        }

        public User getParticipantUser() {
            return participantUser;
        }
    }
}

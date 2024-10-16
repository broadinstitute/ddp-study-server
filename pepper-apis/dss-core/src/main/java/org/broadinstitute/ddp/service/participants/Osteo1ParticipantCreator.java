package org.broadinstitute.ddp.service.participants;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstance;
import org.broadinstitute.ddp.db.dao.JdbiActivityInstanceStatus;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.MedicalProviderDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.CreateTemporaryUserPayload;
import org.broadinstitute.ddp.json.CreateTemporaryUserResponse;
import org.broadinstitute.ddp.json.GovernedUserRegistrationPayload;
import org.broadinstitute.ddp.json.UserRegistrationPayload;
import org.broadinstitute.ddp.json.UserRegistrationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.users.models.EmailAddress;
import org.broadinstitute.ddp.json.users.requests.UserCreationPayload;
import org.broadinstitute.ddp.json.users.responses.UserCreationResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.InstitutionType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.route.CreateTemporaryUserRoute;
import org.broadinstitute.ddp.route.UserRegistrationRoute;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.DateTimeUtils;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.I18nUtil;
import org.broadinstitute.ddp.util.QuestionAnswersUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class Osteo1ParticipantCreator {

    private final TaskPublisher taskPublisher;
    private final Map<String, String> payloadMap = Map.of("studyName", "Osteo");
    private final String eventPayload = GsonUtil.standardGson().toJson(payloadMap);
    private long ddpCreatedAt = Instant.parse("2022-07-09T15:35:00Z").toEpochMilli(); //default v1 activity created date
    private String auth0ClientId = null;
    private String auth0Domain = null;
    Auth0ManagementClient mgmtClient = null;

    public Osteo1ParticipantCreator(TaskPublisher taskPublisher, Auth0ManagementClient auth0ManagementClient,
                                    String auth0ClientId) {
        this.taskPublisher = taskPublisher;
        this.auth0Domain = auth0ManagementClient.getDomain();
        this.mgmtClient = auth0ManagementClient;
        this.auth0ClientId = auth0ClientId;
    }

    public UserCreationResponse createOsteo1User(Handle handle, Request request, Response response, UserCreationPayload payload)
            throws Exception {
        String studyGuid = payload.getStudyGuid();
        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        String os1DefaultPwd = auth0Config.getString(ConfigFile.OS1_USER_DEFAULT_PWD);

        String consentGuid = null;
        String releaseGuid = null;
        LocalDate birthDate = payload.getBirthDate();
        LocalDate today = LocalDate.now();
        boolean self = false;
        boolean parental = false;
        boolean ped7plus = false;
        int age = today.minusYears(birthDate.getYear()).getYear();

        if (age >= 18) {
            self = true;
            consentGuid = "CONSENT";
            releaseGuid = "RELEASE_SELF";
        } else if (age >= 7) {
            ped7plus = true;
            consentGuid = "CONSENT_ASSENT";
            releaseGuid = "RELEASE_MINOR";
        } else {
            parental = true;
            consentGuid = "PARENTAL_CONSENT";
            releaseGuid = "RELEASE_MINOR";
        }

        final EmailAddress email;

        try {
            email = new EmailAddress(payload.getEmail());
        } catch (IllegalArgumentException exception) {
            var invalidEmail = new ApiError(ErrorCodes.BAD_PAYLOAD, "The email address is missing or malformed.");
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, invalidEmail);
        }

        log.info("Attempting to create a OS1 user for study {}", payload.getStudyGuid());

        boolean finalSelf = self;
        String finalConsentGuid = consentGuid;
        String finalReleaseGuid = releaseGuid;
        boolean finalParental = parental;
        boolean finalPed7plus = ped7plus;

        Auth0Util auth0Util = new Auth0Util(auth0Domain);

        //CreateTempUser
        CreateTemporaryUserPayload tempUserPayload = new CreateTemporaryUserPayload(auth0ClientId, auth0Domain);
        CreateTemporaryUserRoute createTempUserRoute = new CreateTemporaryUserRoute();
        CreateTemporaryUserResponse tempUserResp = (CreateTemporaryUserResponse) createTempUserRoute.handle(
                request, response, tempUserPayload);
        String tempUserGuid = tempUserResp.getUserGuid();

        //create Auth0 user
        com.auth0.json.mgmt.users.User newAuth0User = auth0Util.createAuth0User(
                payload.getEmail(), os1DefaultPwd, mgmtClient.getToken());
        mgmtClient.setUserGuidForAuth0User(newAuth0User.getId(), auth0ClientId, tempUserGuid);

        //register user
        long now = Instant.now().toEpochMilli();
        PexInterpreter interpreter = new TreeWalkInterpreter();
        UserRegistrationPayload userRegPayload = new UserRegistrationPayload(newAuth0User.getId(),
                auth0ClientId, payload.getStudyGuid(), auth0Domain,
                tempUserGuid, true);
        if (finalSelf) {
            userRegPayload.setFirstName(payload.getFirstName());
            userRegPayload.setLastName(payload.getLastName());
        } else {
            userRegPayload.setFirstName(payload.getFirstName() + "-proxy");
            userRegPayload.setLastName(payload.getLastName() + "-proxy");
        }

        UserRegistrationRoute userRegistrationRoute = new UserRegistrationRoute(interpreter, new TaskPubSubPublisher());
        UserRegistrationResponse responseReg = (UserRegistrationResponse) userRegistrationRoute.handle(
                request, response, userRegPayload);

        UserDao userDao = handle.attach(UserDao.class);
        User newUser = userDao.findUserByGuidOrAltPid(responseReg.getDdpUserGuid()).get();
        String userGuid = newUser.getGuid();
        UserProfileDao userProfileDao = handle.attach(UserProfileDao.class);
        UserProfile userProfile = userProfileDao.findProfileByUserGuid(userGuid).get();
        userProfile = updateProfile(handle, userProfile, payload.getBirthDate());

        //create PREQUAL, CONSENT & MEDICAL_RELEASE
        ActivityInstanceDto prequalDto = createActivityInstance(handle, "PREQUAL", studyGuid, userGuid, ddpCreatedAt);
        ActivityInstanceDto consentDto = null;
        ActivityInstanceDto releaseDto = null;
        if (finalSelf) {
            consentDto = createActivityInstance(handle, finalConsentGuid, studyGuid, userGuid, ddpCreatedAt);
            releaseDto = createActivityInstance(handle, finalReleaseGuid, studyGuid, userGuid, ddpCreatedAt);
        }
        newUser.setEmail(payload.getEmail());

        //if governed user, create governed user
        UserRegistrationResponse governedUserRegResponse = null;
        String govUserGuid = null;
        User governedUser = null;
        if (!finalSelf) {
            GovernedUserRegistrationPayload governedUserRegPayload = new GovernedUserRegistrationPayload(
                    "en", payload.getFirstName(), payload.getLastName(), null);
            governedUserRegResponse = registerGovernedUser(handle, governedUserRegPayload, birthDate, studyGuid,
                    newUser.getGuid(), auth0ClientId);
            govUserGuid = governedUserRegResponse.getDdpUserGuid();
            governedUser = userDao.findUsersAndProfilesByGuids(Set.of(govUserGuid)).findFirst().get();
            consentDto = createActivityInstance(handle, finalConsentGuid, studyGuid, govUserGuid, ddpCreatedAt);
            releaseDto = createActivityInstance(handle, finalReleaseGuid, studyGuid, govUserGuid, ddpCreatedAt);
        }

        AnswerDao answerDao = handle.attach(AnswerDao.class);
        JdbiActivityInstanceStatus jdbiActivityInstanceStatus = handle.attach(JdbiActivityInstanceStatus.class);
        JdbiActivityInstance jdbiActivityInstance = handle.attach(JdbiActivityInstance.class);

        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        if (finalSelf) {
            populatePrequal(newUser.getGuid(), answerDao, jdbiActivityInstanceStatus, prequalDto, Long.valueOf(age));
            populateSelfConsent(newUser.getGuid(), answerDao, jdbiActivityInstanceStatus, consentDto, birthDate,
                    payload.getFirstName(), payload.getLastName());
            populateRelease(handle, newUser.getGuid(), answerDao, jdbiActivityInstanceStatus, releaseDto,
                    newUser.getId(), studyDto.getId(), "RELEASE_SELF_AGREEMENT");
        } else if (finalParental) {
            populatePedPrequal(newUser.getGuid(), answerDao, jdbiActivityInstanceStatus, prequalDto, Long.valueOf(age));
            populateParentalConsent(govUserGuid, answerDao, jdbiActivityInstanceStatus, consentDto, birthDate,
                    payload.getFirstName(), payload.getLastName());
            populateRelease(handle, govUserGuid, answerDao, jdbiActivityInstanceStatus, releaseDto, governedUser.getId(),
                    studyDto.getId(), "RELEASE_MINOR_AGREEMENT");
        } else if (finalPed7plus) {
            populatePedPrequal(newUser.getGuid(), answerDao, jdbiActivityInstanceStatus, prequalDto, Long.valueOf(age));
            populateConsentAssent(govUserGuid, answerDao, jdbiActivityInstanceStatus, consentDto, birthDate,
                    payload.getFirstName(), payload.getLastName());
            populateRelease(handle, govUserGuid, answerDao, jdbiActivityInstanceStatus, releaseDto, governedUser.getId(),
                    studyDto.getId(), "RELEASE_MINOR_AGREEMENT");
        }
        jdbiActivityInstance.updateIsReadonlyByGuid(true, consentDto.getGuid());
        jdbiActivityInstance.updateIsReadonlyByGuid(true, releaseDto.getGuid());
        log.debug("prequal instanceID: {} .. guid: {} .. act: {} ", prequalDto.getId(), prequalDto.getGuid(),
                prequalDto.getActivityCode());
        log.debug("consent instanceID: {} .. guid: {} .. act: {}", consentDto.getId(), consentDto.getGuid(),
                consentDto.getActivityCode());
        log.debug("release instanceID: {} .. guid: {} .. act: {}", releaseDto.getId(), releaseDto.getGuid(),
                releaseDto.getActivityCode());

        DataExportDao dataExportDao = handle.attach(DataExportDao.class);
        dataExportDao.queueDataSync(newUser.getId());
        if (governedUser != null) {
            dataExportDao.queueDataSync(govUserGuid);
            publishRegisteredPubSubMessage(studyGuid, govUserGuid);
        } else {
            publishRegisteredPubSubMessage(studyGuid, userGuid);
        }

        //now create new CONSENT
        if (finalSelf) {
            consentDto = createActivityInstance(handle, finalConsentGuid, studyGuid, userGuid, now);
        } else {
            consentDto = createActivityInstance(handle, finalConsentGuid, studyGuid, govUserGuid, now);
        }
        log.debug("created latest CONSENT : {} .. activityCode: {}", consentDto.getGuid(), consentDto.getActivityCode());

        if (finalSelf) {
            return new UserCreationResponse(newUser, userProfile);
        } else {
            governedUser.setEmail(payload.getEmail());
            return new UserCreationResponse(governedUser, governedUser.getProfile());
        }
    }

    private void publishRegisteredPubSubMessage(String studyGuid, String participantGuid) {
        taskPublisher.publishTask(
                TaskPubSubPublisher.TASK_PARTICIPANT_REGISTERED,
                eventPayload, studyGuid, participantGuid);
    }

    private ActivityInstanceDto createActivityInstance(Handle handle, String activityCode, String studyGuid,
                                                       String participantGuid, long createdAt) {
        JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
        JdbiUmbrellaStudy jdbiStudy = handle.attach(JdbiUmbrellaStudy.class);
        StudyDto studyDto = jdbiStudy.findByStudyGuid(studyGuid);
        ActivityDto activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyDto.getId(), activityCode).get();
        long studyActivityId = activityDto.getActivityId();

        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        ActivityInstanceDto dto = activityInstanceDao
                .insertInstance(studyActivityId, participantGuid, participantGuid, InstanceStatusType.CREATED,
                        false,
                        createdAt,
                        null, null, "");
        log.debug("created AI : {} for activity code: {}", dto.getId(), activityCode);
        return dto;
    }

    private void populatePrequal(String participantGuid, AnswerDao answerDao,
                                 JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
                                 ActivityInstanceDto activityDto, Long age) {
        //populate PREQUAL answers
        List<SelectedPicklistOption> options = new ArrayList<>();
        options.add(new SelectedPicklistOption("DIAGNOSED"));
        QuestionAnswersUtil.answerPickListQuestion("PREQUAL_SELF_DESCRIBE", participantGuid, activityDto.getGuid(),
                options, answerDao);

        QuestionAnswersUtil.answerNumericQuestion("SELF_CURRENT_AGE", participantGuid, activityDto.getGuid(),
                age, answerDao);

        List<SelectedPicklistOption> optionsCountry = new ArrayList<>();
        options.add(new SelectedPicklistOption("US"));
        QuestionAnswersUtil.answerPickListQuestion("SELF_COUNTRY", participantGuid, activityDto.getGuid(),
                optionsCountry, answerDao);

        List<SelectedPicklistOption> optionsState = new ArrayList<>();
        options.add(new SelectedPicklistOption("MA"));
        QuestionAnswersUtil.answerPickListQuestion("SELF_STATE", participantGuid, activityDto.getGuid(),
                optionsState, answerDao);

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 500,
                activityDto.getParticipantId());
    }

    private void populatePedPrequal(String participantGuid, AnswerDao answerDao, JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
                                    ActivityInstanceDto activityDto, Long age) {
        //populate Pediatric PREQUAL answers
        List<SelectedPicklistOption> options = new ArrayList<>();
        options.add(new SelectedPicklistOption("CHILD_DIAGNOSED"));
        QuestionAnswersUtil.answerPickListQuestion("PREQUAL_SELF_DESCRIBE", participantGuid, activityDto.getGuid(),
                options, answerDao);

        QuestionAnswersUtil.answerNumericQuestion("CHILD_CURRENT_AGE", participantGuid, activityDto.getGuid(),
                age, answerDao);

        List<SelectedPicklistOption> optionsCountry = new ArrayList<>();
        options.add(new SelectedPicklistOption("US"));
        QuestionAnswersUtil.answerPickListQuestion("CHILD_COUNTRY", participantGuid, activityDto.getGuid(),
                optionsCountry, answerDao);

        List<SelectedPicklistOption> optionsState = new ArrayList<>();
        options.add(new SelectedPicklistOption("MA"));
        QuestionAnswersUtil.answerPickListQuestion("CHILD_STATE", participantGuid, activityDto.getGuid(),
                optionsState, answerDao);

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 500,
                activityDto.getParticipantId());
    }


    private void populateSelfConsent(String participantGuid, AnswerDao answerDao, JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
                                     ActivityInstanceDto activityDto, LocalDate dob, String firstName, String lastName) throws Exception {
        //populate CONSENT answers
        QuestionAnswersUtil.answerTextQuestion("CONSENT_SIGNATURE", participantGuid, activityDto.getGuid(),
                "sign consent ", answerDao);

        QuestionAnswersUtil.answerDateQuestion("CONSENT_DOB", participantGuid, activityDto.getGuid(),
                new DateValue(dob.getYear(), dob.getMonthValue(), dob.getDayOfMonth()), answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("CONSENT_BLOOD", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("CONSENT_TISSUE", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_FIRSTNAME", participantGuid, activityDto.getGuid(),
                firstName, answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_LASTNAME", participantGuid, activityDto.getGuid(),
                lastName, answerDao);

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 1000,
                activityDto.getParticipantId());
    }

    private void populateParentalConsent(String participantGuid, AnswerDao answerDao, JdbiActivityInstanceStatus jdbiActivityInstanceStatus,
                                         ActivityInstanceDto activityDto, LocalDate dob,
                                         String firstName, String lastName) throws Exception {
        //populate CONSENT answers
        QuestionAnswersUtil.answerTextQuestion("PARENTAL_CONSENT_SIGNATURE", participantGuid, activityDto.getGuid(),
                "signature parental consent", answerDao);

        QuestionAnswersUtil.answerDateQuestion("PARENTAL_CONSENT_CHILD_DOB", participantGuid, activityDto.getGuid(),
                new DateValue(dob.getYear(), dob.getMonthValue(), dob.getDayOfMonth()), answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("PARENTAL_CONSENT_BLOOD", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("PARENTAL_CONSENT_TISSUE", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerTextQuestion("PARENTAL_CONSENT_FIRSTNAME", participantGuid, activityDto.getGuid(),
                "OS1 user fn", answerDao);

        QuestionAnswersUtil.answerTextQuestion("PARENTAL_CONSENT_LASTNAME", participantGuid, activityDto.getGuid(),
                "lastName1", answerDao);

        QuestionAnswersUtil.answerTextQuestion("PARENTAL_CONSENT_CHILD_FIRSTNAME", participantGuid, activityDto.getGuid(),
                firstName, answerDao);

        QuestionAnswersUtil.answerTextQuestion("PARENTAL_CONSENT_CHILD_LASTNAME", participantGuid, activityDto.getGuid(),
                lastName, answerDao);

        List<SelectedPicklistOption> optionsRelation = new ArrayList<>();
        optionsRelation.add(new SelectedPicklistOption("PARENT"));
        QuestionAnswersUtil.answerPickListQuestion("PARENTAL_CONSENT_RELATIONSHIP", participantGuid, activityDto.getGuid(),
                optionsRelation, answerDao);

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 1000,
                activityDto.getParticipantId());
    }

    private void populateConsentAssent(String participantGuid, AnswerDao answerDao,
                                       JdbiActivityInstanceStatus jdbiActivityInstanceStatus, ActivityInstanceDto activityDto,
                                       LocalDate dob, String firstName, String lastName) throws Exception {
        //populate CONSENT_ASSENT answers
        QuestionAnswersUtil.answerDateQuestion("CONSENT_ASSENT_CHILD_DOB", participantGuid, activityDto.getGuid(),
                new DateValue(dob.getYear(), dob.getMonthValue(), dob.getDayOfMonth()), answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("CONSENT_ASSENT_BLOOD", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerBooleanQuestion("CONSENT_ASSENT_TISSUE", participantGuid, activityDto.getGuid(),
                true, answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_ASSENT_FIRSTNAME", participantGuid, activityDto.getGuid(),
                "OS1 user parent fn", answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_ASSENT_LASTNAME", participantGuid, activityDto.getGuid(),
                "parent lastName1", answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_ASSENT_CHILD_FIRSTNAME", participantGuid, activityDto.getGuid(),
                firstName, answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_ASSENT_CHILD_LASTNAME", participantGuid, activityDto.getGuid(),
                lastName, answerDao);

        List<SelectedPicklistOption> optionsRelation = new ArrayList<>();
        optionsRelation.add(new SelectedPicklistOption("PARENT"));
        QuestionAnswersUtil.answerPickListQuestion("CONSENT_ASSENT_RELATIONSHIP", participantGuid, activityDto.getGuid(),
                optionsRelation, answerDao);

        QuestionAnswersUtil.answerTextQuestion("CONSENT_ASSENT_CHILD_SIGNATURE", participantGuid, activityDto.getGuid(),
                "sign consent", answerDao);

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 1000,
                activityDto.getParticipantId());
    }

    String getMedicalProviderGuid(Handle handle) {
        return DBUtils.uniqueStandardGuid(handle,
                SqlConstants.MedicalProviderTable.TABLE_NAME, SqlConstants.MedicalProviderTable.MEDICAL_PROVIDER_GUID);
    }

    private void populateRelease(Handle handle, String participantGuid, AnswerDao answerDao,
                                 JdbiActivityInstanceStatus jdbiActivityInstanceStatus, ActivityInstanceDto activityDto,
                                 long userId, long studyId, String agreementStableId) throws Exception {

        QuestionAnswersUtil.answerAgreementQuestion(agreementStableId, participantGuid, activityDto.getGuid(),
                true, answerDao);

        String guid = getMedicalProviderGuid(handle);
        MedicalProviderDao medicalProviderDao = handle.attach(MedicalProviderDao.class);
        medicalProviderDao.insert(new MedicalProviderDto(
                null,
                guid,
                userId,
                studyId,
                InstitutionType.INSTITUTION,
                "Brigham And Women's Hospital",
                "Dr Pepper Jr",
                "Boston",
                "MA",
                "UNITED STATES",
                null,
                null,
                null,
                null
        ));

        guid = getMedicalProviderGuid(handle);
        medicalProviderDao.insert(new MedicalProviderDto(
                null,
                guid,
                userId,
                studyId,
                InstitutionType.PHYSICIAN,
                "Massachusetts General Hospital",
                "Dr Pepper",
                "Boston",
                "MA",
                "UNITED STATES",
                null,
                null,
                null,
                null
        ));

        jdbiActivityInstanceStatus.insert(activityDto.getId(), InstanceStatusType.COMPLETE, ddpCreatedAt + 2000,
                activityDto.getParticipantId());
    }

    private UserRegistrationResponse registerGovernedUser(Handle handle, GovernedUserRegistrationPayload payload, LocalDate birthDate,
                                                          String studyGuid, String operatorGuid, String client) {
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        UserDao userDao = handle.attach(UserDao.class);
        StudyDto study = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
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
            var profileDao = handle.attach(UserProfileDao.class);
            //try to get Operator/Proxy users preferred language code
            UserProfile userProfile = profileDao.findProfileByUserGuid(operatorUser.getGuid()).orElse(null);
            preferredLanguageCode = userProfile != null ? userProfile.getPreferredLangCode() : preferredLanguageCode;
            payload.setLanguageCode(preferredLanguageCode);
        }

        initializeProfile(handle, governedUser, studyGuid, payload, birthDate);

        GovernancePolicy policy = handle.attach(StudyGovernanceDao.class).findPolicyByStudyGuid(studyGuid).orElse(null);
        if (policy != null && !policy.getAgeOfMajorityRules().isEmpty()) {
            handle.attach(StudyGovernanceDao.class).addAgeUpCandidate(policy.getStudyId(), governedUser.getId(), operatorUser.getId());
            log.info("Added governed user {} as age-up candidate in study {}", governedUser.getGuid(), policy.getStudyGuid());
        }

        handle.attach(JdbiUserStudyEnrollment.class)
                .changeUserStudyEnrollmentStatus(governedUser.getGuid(), studyGuid, EnrollmentStatusType.REGISTERED);
        log.info("Registered user {} with status {} in study {}", governedUser.getGuid(), EnrollmentStatusType.REGISTERED, studyGuid);

        handle.attach(DataExportDao.class).queueDataSync(governedUser.getId());
        return new UserRegistrationResponse(governedUser.getGuid());

    }

    private void initializeProfile(Handle handle, User user, String studyGuid, GovernedUserRegistrationPayload payload,
                                   LocalDate birthDate) {
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
                .birthDate(birthDate)
                .build();
        profileDao.createProfile(profile);
        log.info("Initialized user profile for user with guid {}", user.getGuid());
    }

    private UserProfile updateProfile(Handle handle, UserProfile userProfile, LocalDate birthDate) {
        UserProfile profileUpd = UserProfile.builder()
                .userId(userProfile.getUserId())
                .firstName(userProfile.getFirstName())
                .lastName(userProfile.getLastName())
                .preferredLangId(userProfile.getPreferredLangId())
                .preferredLangCode(null)
                .timeZone(userProfile.getTimeZone())
                .birthDate(birthDate)
                .build();

        var profileDao = handle.attach(UserProfileDao.class);
        return profileDao.updateProfile(profileUpd);

    }
}

package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static java.util.stream.Collectors.toList;
import static org.broadinstitute.ddp.util.GuidUtils.UPPER_ALPHA_NUMERIC;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.auth0.exception.Auth0Exception;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.typesafe.config.Config;
import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import io.restassured.response.Response;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.API;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiMailingList;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.EnrollmentStatusDto;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.UserRegistrationPayload;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ActivityResponse;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TimestampUtil;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class UserRegistrationRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(UserRegistrationRouteTest.class);

    private static final String EN_LANG_CODE = "en";

    private static String auth0UserId;
    private static String auth0ClientId;
    private static String token;
    private static String url;
    private static String auth0Domain;
    private static String testUserEmail;

    private static StudyDto study1;
    private static StudyDto study2;
    private static StudyDto tempStudy;

    private static long user1Id;
    private static long user2Id;
    private static long user3Id;

    private static String user1Guid;
    private static String user2Guid;
    private static String user3Guid;

    private Set<String> auth0UserIdsToDelete = new HashSet<>();
    private Set<String> userGuidsToDelete = new HashSet<>();

    @BeforeClass
    public static void setup() throws Exception {
        Config cfg = RouteTestUtil.getConfig();
        token = RouteTestUtil.loginStaticTestUserForToken();

        DecodedJWT jwt = JWT.decode(token);
        auth0UserId = jwt.getSubject();
        auth0Domain = jwt.getClaim(Auth0Constants.DDP_TENANT_CLAIM).asString();
        auth0ClientId = RouteTestUtil.getAuth0TestClientId();
        testUserEmail = cfg.getConfig(ConfigFile.AUTH0).getString(ConfigFile.Auth0Testing.AUTH0_TEST_EMAIL);

        url = RouteTestUtil.getTestingBaseUrl() + API.REGISTRATION;

        TransactionWrapper.useTxn(handle -> {
            study1 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            tempStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);

            ClientDao clientDao = handle.attach(ClientDao.class);
            Long clientId1 = clientDao.registerClient(
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    Collections.singletonList(study1.getGuid()),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    study1.getAuth0TenantId());

            Long clientId2 = clientDao.registerClient(
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    Collections.singletonList(study1.getGuid()),
                    GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                    study2.getAuth0TenantId());

            user1Guid = DBUtils.uniqueUserGuid(handle);
            String userHruid = DBUtils.uniqueUserHruid(handle);
            user1Id = handle.attach(JdbiUser.class)
                    .insert(GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                            user1Guid,
                            clientId1,
                            userHruid);

            user2Guid = DBUtils.uniqueUserGuid(handle);
            userHruid = DBUtils.uniqueUserHruid(handle);
            user2Id = handle.attach(JdbiUser.class)
                    .insert(GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                            user2Guid,
                            clientId1,
                            userHruid);

            user3Guid = DBUtils.uniqueUserGuid(handle);
            userHruid = DBUtils.uniqueUserHruid(handle);
            user3Id = handle.attach(JdbiUser.class)
                    .insert(GuidUtils.randomStringFromDictionary(UPPER_ALPHA_NUMERIC, 20),
                            user3Guid,
                            clientId2,
                            userHruid);
        });
    }

    @Before
    public void setupUserStudyEnrollments() {
        cleanup();
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(user1Guid,
                    study1.getGuid(), EnrollmentStatusType.REGISTERED);

            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(user2Guid,
                    study1.getGuid(), EnrollmentStatusType.REGISTERED);

            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(user3Guid,
                    study2.getGuid(), EnrollmentStatusType.REGISTERED);
        });
    }

    @After
    public void cleanup() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            List<EnrollmentStatusDto> enrollments = jdbiEnrollment.findByStudyGuid(study1.getGuid());
            enrollments.addAll(jdbiEnrollment.findByStudyGuid(study2.getGuid()));
            enrollments.addAll(jdbiEnrollment.findByStudyGuid(tempStudy.getGuid()));
            for (EnrollmentStatusDto enrollment : enrollments) {
                jdbiEnrollment.deleteById(enrollment.getUserStudyEnrollmentId());
            }

            String existingUserGuid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);
            long existingUserId = handle.attach(UserDao.class).findUserByGuid(existingUserGuid).get().getId();
            handle.attach(UserGovernanceDao.class).deleteAllGovernancesForProxy(existingUserId);
        });

        var mgmtClient = TransactionWrapper.withTxn(handle ->
                Auth0Util.getManagementClientForDomain(handle, auth0Domain));
        Auth0Util auth0Util = new Auth0Util(auth0Domain);

        for (String auth0UserId : auth0UserIdsToDelete) {
            RouteTestUtil.deleteUserByAuth0UserId(auth0UserId, auth0Domain);
            try {
                auth0Util.deleteAuth0User(auth0UserId, mgmtClient.getToken());
            } catch (Auth0Exception e) {
                throw new RuntimeException(e);
            }
        }
        auth0UserIdsToDelete.clear();

        TransactionWrapper.useTxn(handle -> {
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            var profileDao = handle.attach(UserProfileDao.class);
            DataExportDao dataExportDao = handle.attach(DataExportDao.class);
            for (String userGuid : userGuidsToDelete) {
                long userId = jdbiUser.getUserIdByGuid(userGuid);
                dataExportDao.deleteDataSyncRequestsForUser(userId);
                profileDao.getUserProfileSql().deleteByUserId(userId);
            }
            int numDeleted = jdbiUser.deleteAllByGuids(userGuidsToDelete);
            assertEquals(userGuidsToDelete.size(), numDeleted);
            userGuidsToDelete.clear();
        });
    }

    @Test
    public void testTwoUsersSeparatedByDelta() throws InterruptedException {
        long timeBeforeFirstUser = Instant.now().toEpochMilli();
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user1Guid, study1.getGuid(), EnrollmentStatusType.ENROLLED);
        });
        Thread.sleep(500);
        long timeInBetweenFirstAndSecondUser = Instant.now().toEpochMilli();
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user2Guid, study1.getGuid(), EnrollmentStatusType.ENROLLED);
        });

        List<EnrollmentStatusDto> shouldBeBothUsers =
                TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(study1.getGuid(), timeBeforeFirstUser));

        List<Long> userIds = shouldBeBothUsers.stream()
                .map(s -> s.getUserId())
                .collect(toList());

        assertTrue(userIds.size() == 2);
        assertTrue(userIds.contains(user1Id));
        assertTrue(userIds.contains(user2Id));

        List<EnrollmentStatusDto> shouldBeSecondUser =
                TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(study1.getGuid(), timeInBetweenFirstAndSecondUser));

        userIds = shouldBeSecondUser.stream()
                .map(s -> s.getUserId())
                .collect(toList());

        assertTrue(userIds.size() == 1);
        assertTrue(userIds.contains(user2Id));
    }

    @Test
    public void testSameUserWithSecondUpsert() throws InterruptedException {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user1Guid, study1.getGuid(), EnrollmentStatusType.ENROLLED);
        });
        Thread.sleep(500);
        long timeInBetweenFirstAndSecondUpsert = Instant.now().toEpochMilli();

        List<EnrollmentStatusDto> shouldBeNoUsers =
                TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(study1.getGuid(), timeInBetweenFirstAndSecondUpsert));

        assertTrue(shouldBeNoUsers.isEmpty());

        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user1Guid, study1.getGuid(), EnrollmentStatusType.ENROLLED);
        });

        List<EnrollmentStatusDto> nowUserShouldBeThere =
                TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(study1.getGuid(), timeInBetweenFirstAndSecondUpsert));

        List<Long> userIds = nowUserShouldBeThere.stream()
                .map(s -> s.getUserId())
                .collect(toList());

        assertTrue(userIds.size() == 1);
        assertTrue(userIds.contains(user1Id));
    }

    @Test
    public void testTwoUsersTwoStudies() throws InterruptedException {
        long timeBeforeFirstUser = Instant.now().toEpochMilli();
        TransactionWrapper.useTxn(handle -> {
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user1Guid, study1.getGuid(), EnrollmentStatusType.ENROLLED);
            handle.attach(JdbiUserStudyEnrollment.class)
                    .changeUserStudyEnrollmentStatus(user3Guid, study2.getGuid(), EnrollmentStatusType.ENROLLED);
        });

        List<EnrollmentStatusDto> shouldBeOneUser =
                TransactionWrapper.withTxn(handle -> handle.attach(JdbiUserStudyEnrollment.class)
                        .findByStudyGuidAfterOrEqualToInstant(study1.getGuid(), timeBeforeFirstUser));

        List<Long> userIds = shouldBeOneUser.stream()
                .filter(enrolledUser -> enrolledUser.getEnrollmentStatus() == EnrollmentStatusType.ENROLLED)
                .map(s -> s.getUserId())
                .collect(toList());

        assertTrue("We expected 1, but found " + userIds.size() + " users", userIds.size() == 1);
        assertTrue(userIds.contains(user1Id));
    }

    @Test
    public void testRegister_missingRequired() {
        UserRegistrationPayload payload = new UserRegistrationPayload(null, null, null, null, null);
        makeRequestWith(payload).then().assertThat()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", allOf(containsString("auth0ClientId"), containsString("studyGuid")));
    }

    @Test
    public void testRegister_missingDomain() {
        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), null);
        makeRequestWith(payload).then().assertThat()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("auth0Domain"));
    }

    @Test
    public void testRegister_missingUserId() {
        UserRegistrationPayload payload = new UserRegistrationPayload(null, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), auth0Domain);
        makeRequestWith(payload).then().assertThat()
                .statusCode(400)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.BAD_PAYLOAD))
                .body("message", containsString("auth0UserId"));
    }

    @Test
    public void testRegister_revokedClient() throws Exception {
        RouteTestUtil.revokeTestClient();
        try {
            UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                    EN_LANG_CODE, study1.getGuid(), auth0Domain);
            makeRequestWith(payload).then().assertThat().statusCode(401);
        } finally {
            RouteTestUtil.enableTestClient();
        }
    }

    @Test
    public void testRegister_studyInDifferentDomain() {
        StudyDto study = TransactionWrapper.withTxn(handle -> {
            Config auth0Cfg = RouteTestUtil.getConfig().getConfig(ConfigFile.AUTH0);
            String auth0Domain = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_DOMAIN2);
            String mgmtClientId = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID2);
            String mgmtSecret = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET2);
            return TestDataSetupUtil.generateTestStudy(handle, auth0Domain, mgmtClientId, mgmtSecret);
        });

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, study.getGuid(), auth0Domain);

        makeRequestWith(payload).then().assertThat()
                .statusCode(404)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.STUDY_NOT_FOUND))
                .body("message", containsString(study.getGuid()));
    }

    @Test
    public void testRegister_newUser() throws Exception {
        String testAuth0UserId = TransactionWrapper.withTxn(handle -> {
            // Have to create a completely new user that only exists in Auth0, so that email-lookup-by-user will not
            // fail and we can test that user/profile is created post-registration.
            var mgmtClient = Auth0Util.getManagementClientForDomain(handle, auth0Domain);
            return new Auth0Util(auth0Domain).createTestingUser(mgmtClient.getToken()).getAuth0Id();
        });
        auth0UserIdsToDelete.add(testAuth0UserId);

        UserRegistrationPayload payload = new UserRegistrationPayload(testAuth0UserId, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), auth0Domain);

        Instant start = Instant.now();
        String newUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", Matchers.not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");

        TransactionWrapper.useTxn(handle -> {
            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(newUserGuid);
            assertNotNull(userDto);

            assertTrue(start.toEpochMilli() <= userDto.getCreatedAtMillis());
            assertEquals(userDto.getCreatedAtMillis(), userDto.getUpdatedAtMillis());

            UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserId(userDto.getUserId()).get();

            Long enLanguageCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(EN_LANG_CODE);
            assertEquals(profile.getPreferredLangId(), enLanguageCodeId);

            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            Optional<EnrollmentStatusType> enrollment = jdbiEnrollment
                    .getEnrollmentStatusByUserAndStudyGuids(newUserGuid, study1.getGuid());

            assertTrue(enrollment.isPresent());
            assertEquals(EnrollmentStatusType.REGISTERED, enrollment.get());
        });
    }

    @Test
    public void testRegister_newUser_governancePolicy_shouldNotCreateGovernedUser() {
        StudyDto testStudy = TransactionWrapper.withTxn(handle -> {
            StudyDto study = TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig());
            GovernancePolicy policy = new GovernancePolicy(study.getId(), new Expression("false"));
            handle.attach(StudyGovernanceDao.class).createPolicy(policy);
            return study;
        });

        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId));
        userGuidsToDelete.add(tempUser.getGuid());

        long count = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).getTotalUserCount());

        String fakeAuth0Id = "fake|" + Instant.now().toEpochMilli();
        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, testStudy.getGuid(), auth0Domain, tempUser.getGuid(), null);

        String operatorUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", Matchers.not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");
        assertEquals(tempUser.getGuid(), operatorUserGuid);

        TransactionWrapper.useTxn(handle -> {
            long actualCount = handle.attach(JdbiUser.class).getTotalUserCount();
            assertEquals("New users should not be created", count, actualCount);
            assertEquals("Governance relationships should not be present for operator",
                    0, handle.attach(UserGovernanceDao.class).findGovernancesByProxyGuid(operatorUserGuid).count());

            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            assertTrue("Operator should be registered in study",
                    jdbiEnrollment.findIdByUserAndStudyGuid(operatorUserGuid, testStudy.getGuid()).isPresent());

            // Cleanup data
            jdbiEnrollment.deleteByUserGuidStudyGuid(operatorUserGuid, testStudy.getGuid());
        });
    }

    @Test
    public void testRegister_newUser_governancePolicy_createGovernedUser() {
        StudyDto testStudy = TransactionWrapper.withTxn(handle -> {
            StudyDto study = TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig());
            GovernancePolicy policy = new GovernancePolicy(study.getId(), new Expression("true"));
            policy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 21, 4));
            handle.attach(StudyGovernanceDao.class).createPolicy(policy);
            return study;
        });

        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId));
        userGuidsToDelete.add(tempUser.getGuid());

        ActivityInstanceDto instanceDto = TransactionWrapper.withTxn(handle -> {
            User studyAdminUser = handle.attach(UserDao.class).createUser(auth0Domain, auth0ClientId, null);
            String activityCode = "ACT" + Instant.now().toEpochMilli();
            FormActivityDef prequal = FormActivityDef
                    .generalFormBuilder(activityCode, "v1", testStudy.getGuid())
                    .addName(new Translation("en", "a test activity"))
                    .build();
            handle.attach(ActivityDao.class).insertActivity(prequal, RevisionMetadata.now(studyAdminUser.getId(), "test"));
            return handle.attach(ActivityInstanceDao.class)
                    .insertInstance(prequal.getActivityId(), tempUser.getGuid());
        });

        long count = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).getTotalUserCount());

        String fakeAuth0Id = "fake|" + Instant.now().toEpochMilli();
        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, testStudy.getGuid(), auth0Domain, tempUser.getGuid(), null);

        String operatorUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", Matchers.not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");
        assertEquals(tempUser.getGuid(), operatorUserGuid);

        TransactionWrapper.useTxn(handle -> {
            long actualCount = handle.attach(JdbiUser.class).getTotalUserCount();
            assertEquals("A new user should be created", count + 1, actualCount);

            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            assertFalse("Operator should not be registered in study",
                    jdbiEnrollment.findIdByUserAndStudyGuid(operatorUserGuid, testStudy.getGuid()).isPresent());

            UserGovernanceDao governanceDao = handle.attach(UserGovernanceDao.class);
            List<Governance> governances = governanceDao
                    .findActiveGovernancesByProxyAndStudyGuids(operatorUserGuid, testStudy.getGuid())
                    .collect(toList());
            assertEquals("Governance relationships should be present for operator", 1, governances.size());
            assertEquals(tempUser.getId(), governances.get(0).getProxyUserId());
            assertEquals(operatorUserGuid, governances.get(0).getProxyUserGuid());

            User governedUser = handle.attach(UserDao.class).findUserById(governances.get(0).getGovernedUserId()).get();
            userGuidsToDelete.add(governedUser.getGuid());
            assertNotNull("Governed user should have profile initialized",
                    handle.attach(UserProfileDao.class).findProfileByUserId(governedUser.getId()).get());
            assertTrue("Governed user should be registered in study",
                    jdbiEnrollment.findIdByUserAndStudyGuid(governedUser.getGuid(), testStudy.getGuid()).isPresent());

            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            List<ActivityResponse> instances = instanceDao.findBaseResponsesByStudyAndUserGuid(testStudy.getGuid(), operatorUserGuid);
            assertTrue("Operator should not have any activity instances", instances.isEmpty());

            instances = instanceDao.findBaseResponsesByStudyAndUserGuid(testStudy.getGuid(), governedUser.getGuid());
            assertEquals("Governed user should have been assigned activity instances", 1, instances.size());
            assertEquals(instanceDto.getGuid(), instances.get(0).getGuid());
            assertEquals(governedUser.getId(), instances.get(0).getParticipantId());

            StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);
            Optional<AgeUpCandidate> candidate = studyGovernanceDao.findAgeUpCandidate(testStudy.getId(), governedUser.getId());
            assertTrue("Governed user should be added as age-up candidate because policy has AoM rules", candidate.isPresent());

            // Cleanup data
            instanceDao.deleteByInstanceGuid(instanceDto.getGuid());
            governanceDao.deleteAllGovernancesForProxy(tempUser.getId());
            jdbiEnrollment.deleteByUserGuidStudyGuid(governedUser.getGuid(), testStudy.getGuid());
            studyGovernanceDao.removeAgeUpCandidates(Set.of(candidate.get().getId()));
        });
    }

    @Test
    public void testRegister_existingUser() {
        String expectedUserGuid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), auth0Domain);

        makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", equalTo(expectedUserGuid));
    }

    @Test
    public void testRegister_existingUser_notInStudyAndLoggingIn_fails() {
        String existingUserGuid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);

        TransactionWrapper.useTxn(handle -> assertFalse(handle.attach(JdbiUserStudyEnrollment.class)
                .getEnrollmentStatusByUserAndStudyGuids(existingUserGuid, tempStudy.getGuid())
                .isPresent()));

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, null, "login");

        makeRequestWith(payload).then().assertThat()
                .statusCode(HttpStatus.SC_UNPROCESSABLE_ENTITY)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.SIGNUP_REQUIRED))
                .body("message", containsString("needs to register with study"));
    }

    @Test
    public void testRegister_existingUser_notInStudyButIsProxy_returnsOperatorGuid() {
        String existingUserGuid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);

        TransactionWrapper.useTxn(handle -> {
            assertFalse(handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyGuids(existingUserGuid, tempStudy.getGuid())
                    .isPresent());
            long existingUserId = handle.attach(UserDao.class).findUserByGuid(existingUserGuid).get().getId();
            long clientId = handle.attach(ClientDao.class).getConfiguration(auth0ClientId, auth0Domain).getClientId();
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            Governance gov = userGovernanceDao.createGovernedUser(clientId, existingUserId, "test-alias");
            userGovernanceDao.grantGovernedStudy(gov.getId(), tempStudy.getGuid());
            userGuidsToDelete.add(gov.getGovernedUserGuid());
        });

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, null, "login");

        makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", equalTo(existingUserGuid));
    }

    @Test
    public void testRegister_mailingList_auth0EmailLookupFails_stillSuccessful() {
        String fakeAuth0Id = "this-has-no-email" + Instant.now().toEpochMilli();

        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), auth0Domain);

        String newUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");

        TransactionWrapper.useTxn(handle -> {
            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            long id = jdbiEnrollment.findIdByUserAndStudyGuid(newUserGuid, study1.getGuid()).get();
            jdbiEnrollment.deleteById(id);
        });

        RouteTestUtil.deleteUserByAuth0UserId(fakeAuth0Id, auth0Domain);
    }

    @Test
    public void testRegister_mailingList_successfulRemovalAfterRegisteredToStudy() {
        // Note: use existing test user, which should have email in Auth0 and not registered with study1 yet.

        long mailListEntryId = TransactionWrapper.withTxn(handle -> {
            JdbiMailingList jdbiMailingList = handle.attach(JdbiMailingList.class);
            int numInserted = jdbiMailingList.insertByStudyGuidIfNotStoredAlready("foo", "bar", testUserEmail,
                    study1.getGuid(), null, Instant.now().toEpochMilli());
            assertEquals("should have inserted to mailing list", 1, numInserted);
            return jdbiMailingList.findIdByEmailAndStudyGuid(testUserEmail, study1.getGuid()).get();
        });

        try {
            UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                    EN_LANG_CODE, study1.getGuid(), auth0Domain);
            makeRequestWith(payload).then().assertThat().statusCode(200);

            TransactionWrapper.useTxn(handle -> {
                Optional<Long> actual = handle.attach(JdbiMailingList.class)
                        .findIdByEmailAndStudyGuid(testUserEmail, study1.getGuid());
                assertFalse("should have removed mailing list entry", actual.isPresent());
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(JdbiMailingList.class).deleteById(mailListEntryId);
            });
        }
    }

    @Test
    public void testRegister_mailingList_emailNotRequiredWhenAlreadyRegisteredWithStudy() {
        // Note: user1 is registered with study1, but user1 does not exist as an auth0 user.

        String auth0UserIdForUser1 = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).findByUserId(user1Id).getAuth0UserId());

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserIdForUser1, auth0ClientId,
                EN_LANG_CODE, study1.getGuid(), auth0Domain);

        makeRequestWith(payload).then().assertThat().statusCode(200);
    }

    @Test
    public void testRegister_eventsAreQueued() {
        StudyDto testStudy = TransactionWrapper.withTxn(handle ->
                TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig()));

        long eventConfigId = TransactionWrapper.withTxn(handle -> {
            long triggerId = handle.attach(EventTriggerDao.class)
                    .insertUserRegisteredTrigger();
            long actionId = handle.attach(EventActionDao.class)
                    .insertNotificationAction(new SendgridEmailEventActionDto("abc", "en"));
            return handle.attach(JdbiEventConfiguration.class)
                    .insert(triggerId, actionId, testStudy.getId(), Instant.now().toEpochMilli(), null, null, null, null, true, 1);
        });

        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId));
        userGuidsToDelete.add(tempUser.getGuid());

        String fakeAuth0Id = "fake|" + Instant.now().toEpochMilli();
        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, testStudy.getGuid(), auth0Domain, tempUser.getGuid(), null);

        String actualUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", Matchers.not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");
        assertEquals(tempUser.getGuid(), actualUserGuid);

        try {
            TransactionWrapper.useTxn(handle -> {
                String query = "select 1 from queued_event where event_configuration_id = ? and participant_user_id = ?";
                assertEquals((Integer) 1, handle.select(query, eventConfigId, tempUser.getId()).mapTo(Integer.class).findOnly());
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(QueuedEventDao.class).deleteQueuedEventsByEventConfigurationId(eventConfigId);
                JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
                for (EnrollmentStatusDto enrollment : jdbiEnrollment.findByStudyGuid(testStudy.getGuid())) {
                    jdbiEnrollment.deleteById(enrollment.getUserStudyEnrollmentId());
                }
            });
        }
    }

    @Test
    public void testUpgradeTempUser_whenGivenGuid_noNewUsersAreCreatedAndUserIsMadePermanent() {
        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId));
        assertNotNull(tempUser.getExpiresAt());
        userGuidsToDelete.add(tempUser.getGuid());

        long count = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).getTotalUserCount());

        String fakeAuth0Id = "fake|" + Instant.now().toEpochMilli();
        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, tempUser.getGuid(), null);

        String actualUserGuid = makeRequestWith(payload).then().assertThat()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("ddpUserGuid", Matchers.not(isEmptyOrNullString()))
                .and().extract().path("ddpUserGuid");

        assertEquals(tempUser.getGuid(), actualUserGuid);

        TransactionWrapper.useTxn(handle -> {
            long actualCount = handle.attach(JdbiUser.class).getTotalUserCount();
            assertEquals(count, actualCount);

            // Permanent in this case means no expiration and has auth0_user_id.
            UserDto actualUser = handle.attach(JdbiUser.class).findByUserGuid(actualUserGuid);
            assertNull(actualUser.getExpiresAtMillis());
            assertEquals(fakeAuth0Id, actualUser.getAuth0UserId());

            UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserId(actualUser.getUserId()).get();
            Long enLanguageCodeId = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(EN_LANG_CODE);
            assertEquals(profile.getPreferredLangId(), enLanguageCodeId);

            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            Optional<EnrollmentStatusType> enrollment = jdbiEnrollment
                    .getEnrollmentStatusByUserAndStudyGuids(actualUserGuid, tempStudy.getGuid());

            assertTrue(enrollment.isPresent());
            assertEquals(EnrollmentStatusType.REGISTERED, enrollment.get());
        });
    }

    @Test
    public void testUpgradeTempUser_whenAuth0IdIsExistingUser_notSupported() {
        User tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId));
        assertNotNull(tempUser.getExpiresAt());
        userGuidsToDelete.add(tempUser.getGuid());

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, tempUser.getGuid(), null);

        makeRequestWith(payload).then().assertThat()
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_SUPPORTED))
                .body("message", containsString("upgrade"));
    }

    @Test
    public void testUpgradeTempUser_whenGivenExpiredTempUser_returnsError() {
        UserDto tempUser = TransactionWrapper.withTxn(handle -> {
            long userId = handle.attach(UserDao.class).createTempUser(auth0Domain, auth0ClientId).getId();
            JdbiUser jdbiUser = handle.attach(JdbiUser.class);
            jdbiUser.updateExpiresAtById(userId, Instant.now().toEpochMilli() - 10000);
            return jdbiUser.findByUserId(userId);
        });
        userGuidsToDelete.add(tempUser.getUserGuid());

        String fakeAuth0Id = "fake|" + Instant.now().toEpochMilli();
        UserRegistrationPayload payload = new UserRegistrationPayload(fakeAuth0Id, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, tempUser.getUserGuid(), null);

        makeRequestWith(payload).then().assertThat()
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.EXPIRED))
                .body("message", containsString("expired"));
    }

    @Test
    public void testUpgradeTempUser_whenGivenNonTempUser_returnsError() {
        String existingUserGuid = RouteTestUtil.getUnverifiedUserGuidFromToken(token);

        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, existingUserGuid, null);

        makeRequestWith(payload).then().assertThat()
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_SUPPORTED))
                .body("message", containsString("existing user to upgrade temporary user"));
    }

    @Test
    public void testUpgradeTempUser_whenGivenNonExistingTempUser_returnsError() {
        UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                EN_LANG_CODE, tempStudy.getGuid(), auth0Domain, "non-existing-temp-guid", null);

        makeRequestWith(payload).then().assertThat()
                .statusCode(422)
                .contentType(ContentType.JSON)
                .body("code", equalTo(ErrorCodes.NOT_SUPPORTED))
                .body("message", containsString("existing user to upgrade temporary user"));
    }

    private GovernancePolicy createGovernancePolicy(long studyId) {
        GovernancePolicy governancePolicy = new GovernancePolicy(studyId, new Expression("true"));

        governancePolicy.addAgeOfMajorityRule(new AgeOfMajorityRule("true", 0, 0));

        return governancePolicy;
    }

    @Test
    public void testAccountInvitation() {
        var user = new AtomicReference<User>();

        AtomicReference<GovernancePolicy> governancePolicy = new AtomicReference<>();
        AtomicReference<StudyDto> testStudy = new AtomicReference<>();
        AtomicBoolean shouldDeleteProfile = new AtomicBoolean(false);
        try {
            var invitation = new AtomicReference<InvitationDto>();

            // clear the auth0 user id from the test account and send in an invitation-based registration
            TransactionWrapper.useTxn(handle -> {
                var userDao = handle.attach(UserDao.class);
                Auth0TenantDto auth0TenantDto = handle.attach(JdbiAuth0Tenant.class).findByDomain(auth0Domain);
                user.set(userDao.findUserByAuth0UserId(auth0UserId, auth0TenantDto.getId()).get());

                testStudy.set(TestDataSetupUtil.generateTestStudy(handle, RouteTestUtil.getConfig()));
                long testStudyId = testStudy.get().getId();
                invitation.set(handle.attach(InvitationFactory.class).createInvitation(InvitationType.AGE_UP,
                        testStudyId, user.get().getId(), "test+" + System.currentTimeMillis() + "@datadonationplatform.org"));

                handle.attach(InvitationDao.class).clearDates(invitation.get().getInvitationGuid());
                userDao.updateAuth0UserId(user.get().getGuid(), null);

                var profileDao = handle.attach(UserProfileDao.class);

                UserProfile profile = profileDao.findProfileByUserId(user.get().getId()).orElse(null);
                if (profile == null) {
                    shouldDeleteProfile.set(true);
                }

                if (profile == null || profile.getBirthDate() == null) {
                    profileDao.getUserProfileSql().upsertBirthDate(user.get().getId(), LocalDate.of(1953, 9, 18));
                }

                var studyGovernanceDao = handle.attach(StudyGovernanceDao.class);

                governancePolicy.set(createGovernancePolicy(testStudyId));
                governancePolicy.set(studyGovernanceDao.createPolicy(governancePolicy.get()));
                LOG.info("Created governance policy {} for study {}", governancePolicy.get().getId(), governancePolicy.get().getStudyId());

                // Create a downstream non-dispatched event that sets user study status to enrolled
                long triggerId = handle.attach(EventTriggerDao.class)
                        .insertStaticTrigger(EventTriggerType.GOVERNED_USER_REGISTERED);
                long actionId = handle.attach(EventActionDao.class)
                        .insertStaticAction(EventActionType.USER_ENROLLED);
                handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, testStudy.get().getId(),
                        Instant.now().toEpochMilli(), null, 0, null, null, false, 1);
            });

            String invitationGuid = invitation.get().getInvitationGuid();

            UserRegistrationPayload payload = new UserRegistrationPayload(auth0UserId, auth0ClientId,
                    EN_LANG_CODE, testStudy.get().getGuid(), auth0Domain, null, null, invitationGuid);

            makeRequestWith(payload).then().assertThat()
                    .statusCode(200)
                    .contentType(ContentType.JSON)
                    .body("ddpUserGuid", Matchers.not(Matchers.isEmptyOrNullString()));

            TransactionWrapper.useTxn(handle -> {
                var updatedInvitation = handle.attach(InvitationDao.class).findByInvitationGuid(invitationGuid).get();
                assertNotNull(updatedInvitation.getAcceptedAt());
                assertTrue(updatedInvitation.getAcceptedAt().before(TimestampUtil.now()));

                User requeriedUser = handle.attach(UserDao.class).findUserByGuid(user.get().getGuid()).get();
                assertEquals(auth0UserId, requeriedUser.getAuth0UserId());

                EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                        .getEnrollmentStatusByUserAndStudyIds(user.get().getId(), testStudy.get().getId())
                        .orElse(null);
                assertNotNull("downstream event for setting status should have been triggered", status);
                assertEquals(EnrollmentStatusType.ENROLLED, status);
            });

        } finally {
            TransactionWrapper.useTxn(handle -> {
                // reset the auth0 user id for the test user
                var userDao = handle.attach(UserDao.class);
                var requieriedUser = userDao.findUserByGuid(user.get().getGuid()).get();
                if (!requieriedUser.hasAuth0Account()) {
                    userDao.updateAuth0UserId(user.get().getGuid(), auth0UserId);
                }

                // remove the governance policy
                if (governancePolicy.get() != null) {
                    handle.attach(StudyGovernanceDao.class).removePolicy(governancePolicy.get().getId());
                }

                if (shouldDeleteProfile.get()) {
                    handle.attach(UserProfileDao.class).getUserProfileSql().deleteByUserId(user.get().getId());
                }

                if (testStudy.get() != null) {
                    handle.attach(JdbiUserStudyEnrollment.class)
                            .deleteByUserGuidStudyGuid(user.get().getGuid(), testStudy.get().getGuid());
                }
            });
        }
    }

    private Response makeRequestWith(UserRegistrationPayload payload) {
        return given().body(payload, ObjectMapperType.GSON).when().post(url);
    }
}

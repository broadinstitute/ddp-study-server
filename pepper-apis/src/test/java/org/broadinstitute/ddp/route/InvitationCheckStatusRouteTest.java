package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.broadinstitute.ddp.json.invitation.InvitationCheckStatusPayload.QUALIFICATION_ZIP_CODE;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.InvitationCheckStatusPayload;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.transformers.NullableJsonTransformer;
import org.broadinstitute.ddp.util.TestServer;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class InvitationCheckStatusRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;

    private Handle mockHandle;
    private JdbiUmbrellaStudy mockJdbiStudy;
    private JdbiClientUmbrellaStudy mockClientStudy;
    private InvitationDao mockInviteDao;
    private InvitationCheckStatusRoute route;
    private TestServer testServer;

    @BeforeClass
    public static void setupData() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            testData.getTestingStudy().setRecaptchaSiteKey("XXXXX");
        });
    }

    @Before
    public void setupMocks() {
        mockHandle = mock(Handle.class);
        mockJdbiStudy = mock(JdbiUmbrellaStudy.class);
        mockClientStudy = mock(JdbiClientUmbrellaStudy.class);
        mockInviteDao = mock(InvitationDao.class);
        doReturn(mockJdbiStudy).when(mockHandle).attach(JdbiUmbrellaStudy.class);
        doReturn(mockClientStudy).when(mockHandle).attach(JdbiClientUmbrellaStudy.class);
        doReturn(mockInviteDao).when(mockHandle).attach(InvitationDao.class);
        route = spy(InvitationCheckStatusRoute.class);
        doReturn(true).when(route).isUserRecaptchaTokenValid(anyString(), anyString(), anyString());

        testServer = new TestServer(
                service -> service.post(RouteConstants.API.INVITATION_CHECK, route, new NullableJsonTransformer())
        ).startServer();
    }

    @After
    public void breakDown() {
        testServer.stopServer();
    }

    @Test
    public void testCheckStatus_studyNotFound() {
        doReturn(null).when(mockJdbiStudy).findByStudyGuid(any());
        var payload = new InvitationCheckStatusPayload("foo", "invite", "mockTokenValue");
        var actual = route.checkStatus(mockHandle, "study", "", payload);
        assertNotNull(actual);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());
    }

    @Test
    public void testCheckStatus_clientNotFound() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(Collections.emptyList()).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());

        var payload = new InvitationCheckStatusPayload("foo", "invite", "mockTokenValue");
        var actual = route.checkStatus(mockHandle, "study", "", payload);
        assertNotNull(actual);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());
    }

    @Test
    public void testCheckStatus_clientNoAccess() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(List.of("study1", "study2")).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());

        var payload = new InvitationCheckStatusPayload("foo", "invite", "mockTokenValue");
        var actual = route.checkStatus(mockHandle, "study", "", payload);
        assertNotNull(actual);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());
    }

    @Test
    public void testCheckStatus_badInvitation() {
        doReturn(testData.getTestingStudy()).when(mockJdbiStudy).findByStudyGuid(any());
        doReturn(List.of(testData.getStudyGuid())).when(mockClientStudy)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(any(), any());
        var payload = new InvitationCheckStatusPayload("foo", "invite", "mockTokenValue");

        doReturn(Optional.empty()).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        var actual = route.checkStatus(mockHandle, "study", "", payload);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());

        var now = Instant.now();
        var voided = fakeInvitation(now, now, null, null);
        doReturn(Optional.of(voided)).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        actual = route.checkStatus(mockHandle, "study", "", payload);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());

        var accepted = fakeInvitation(now, null, null, now);
        doReturn(Optional.of(accepted)).when(mockInviteDao).findByInvitationGuid(anyLong(), any());
        actual = route.checkStatus(mockHandle, "study", "", payload);
        assertEquals(ErrorCodes.INVALID_INVITATION, actual.getCode());
    }

    @Test
    public void testGoodInvitation() {
        doReturn(testData.getTestingStudy()).when(route).findStudy(any(Handle.class), anyString());
        InvitationDto invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new InvitationCheckStatusPayload(
                    testData.getAuth0ClientId(),
                    invitation.getInvitationGuid(), "mockTokenValue");
            String url = testServer.baseUrl() + RouteConstants.API.INVITATION_CHECK;
            url = url.replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(200);
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }

    @Test
    public void testRecaptchaTokens() {
        InvitationDto invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        doReturn(testData.getTestingStudy()).when(route).findStudy(any(Handle.class), anyString());
        String badTokenValue = "BAD-TOKEN!";
        String goodToken = "NICETOKEN!";
        String localIp = "127.0.0.1";
        doReturn(true).when(route)
                .isUserRecaptchaTokenValid(goodToken, testData.getTestingStudy().getRecaptchaSiteKey(), localIp);
        doReturn(false).when(route)
                .isUserRecaptchaTokenValid(badTokenValue, testData.getTestingStudy().getRecaptchaSiteKey(), localIp);
        try {
            var badTokenPayload = new InvitationCheckStatusPayload(testData.getAuth0ClientId(), invitation.getInvitationGuid(),
                    badTokenValue);
            String url = testServer.baseUrl() + RouteConstants.API.INVITATION_CHECK;
            url = url.replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
            given().body(badTokenPayload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(HttpStatus.SC_BAD_REQUEST);

            var goodTokenPayload = new InvitationCheckStatusPayload(testData.getAuth0ClientId(), invitation.getInvitationGuid(),
                    goodToken);
            given().body(goodTokenPayload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(200);

        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }

    @Test
    public void testZipCodeCheck() {
        doReturn(testData.getTestingStudy()).when(route).findStudy(any(Handle.class), anyString());
        var kitConfigId = new AtomicReference<Long>();
        var kitRuleId = new AtomicReference<Long>();
        InvitationDto invitation = TransactionWrapper.withTxn(handle -> {
            var kitDao = handle.attach(KitConfigurationDao.class);
            long configId = kitDao.insertConfiguration(testData.getStudyId(), 1,
                    handle.attach(KitTypeDao.class).getSalivaKitType().getId());
            long ruleId = kitDao.addZipCodeRule(configId, Set.of("12345"));
            kitConfigId.set(configId);
            kitRuleId.set(ruleId);
            return handle.attach(InvitationFactory.class)
                    .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis());
        });

        // Since we are mocking stuff, we need to run our own server
        String url = testServer.baseUrl() + RouteConstants.API.INVITATION_CHECK;
        url = url.replace(RouteConstants.PathParam.STUDY_GUID, testData.getStudyGuid());
        var payload = new InvitationCheckStatusPayload(
                testData.getAuth0ClientId(),
                invitation.getInvitationGuid(), "mockTokenValue");

        try {
            // Test no zip code in request
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(400).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.INVALID_INVITATION_QUALIFICATIONS));

            // Test no zip code match
            payload.getQualificationDetails().put(QUALIFICATION_ZIP_CODE, "foobar");
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(400).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.INVALID_INVITATION_QUALIFICATIONS));

            // Test null zip code
            payload.getQualificationDetails().put(QUALIFICATION_ZIP_CODE, null);
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(400).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.INVALID_INVITATION_QUALIFICATIONS));

            // Test zip code match success
            payload.getQualificationDetails().put(QUALIFICATION_ZIP_CODE, "12345");
            given().body(payload, ObjectMapperType.GSON)
                    .when().post(url)
                    .then().assertThat()
                    .statusCode(200);
        } finally {
            TransactionWrapper.useTxn(handle -> {
                var kitDao = handle.attach(KitConfigurationDao.class);
                kitDao.getJdbiKitRules().deleteRuleFromConfiguration(kitConfigId.get(), kitRuleId.get());
                kitDao.getJdbiKitRules().deleteRuleById(kitRuleId.get());
                kitDao.deleteConfiguration(kitConfigId.get());
                handle.attach(InvitationSql.class).deleteById(invitation.getInvitationId());
            });
        }
    }

    private InvitationDto fakeInvitation(Instant created, Instant voided, Instant verified, Instant accepted) {
        Long userId = accepted == null ? null : testData.getUserId();
        return new InvitationDto(1L, "guid", InvitationType.RECRUITMENT, created, voided, verified, accepted,
                testData.getStudyId(), userId, null, null);
    }
}

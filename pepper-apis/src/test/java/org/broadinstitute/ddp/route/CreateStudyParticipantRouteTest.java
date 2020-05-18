package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.UserProfileSql;
import org.broadinstitute.ddp.json.CreateStudyParticipantPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class CreateStudyParticipantRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String urlTemplate;

    @BeforeClass
    public static void setupData() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.STUDY_PARTICIPANTS
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            handle.attach(AuthDao.class).assignStudyAdmin(testData.getUserId(), testData.getStudyId());
        });
    }

    @AfterClass
    public static void cleanupData() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
        });
    }

    @Test
    public void testNonBlankInvitationIsRequired() {
        var payload = new CreateStudyParticipantPayload("  \t\n");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(400);
    }

    @Test
    public void testInvitationNotFound() {
        var payload = new CreateStudyParticipantPayload("foobar");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testInvitationAlreadyAccepted() {
        var invitation = TransactionWrapper.withTxn(handle -> {
            var invite = handle.attach(InvitationFactory.class)
                    .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis());
            handle.attach(InvitationDao.class).assignAcceptingUser(invite.getInvitationId(), testData.getUserId(), Instant.now());
            return invite;
        });
        try {
            var payload = new CreateStudyParticipantPayload(invitation.getInvitationGuid());
            given().auth().oauth2(testData.getTestingUser().getToken())
                    .pathParam("study", testData.getStudyGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat()
                    .statusCode(422).contentType(ContentType.JSON)
                    .body("code", equalTo(ErrorCodes.INVALID_INVITATION));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                    .deleteById(invitation.getInvitationId()));
        }
    }

    @Test
    public void testSuccess() {
        var invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        var createdUserGuid = new AtomicReference<String>();

        try {
            var payload = new CreateStudyParticipantPayload(invitation.getInvitationGuid());
            String userGuid = given()
                    .auth().oauth2(testData.getTestingUser().getToken())
                    .pathParam("study", testData.getStudyGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("userGuid", not(isEmptyOrNullString()))
                    .extract().body().path("userGuid");
            createdUserGuid.set(userGuid);

            TransactionWrapper.useTxn(handle -> {
                var createdUser = handle.attach(UserDao.class).findUserByGuid(userGuid).get();
                assertNull("should not have auth0 account", createdUser.getAuth0UserId());
                assertTrue("should have user profile", handle.attach(UserProfileDao.class)
                        .findProfileByUserGuid(userGuid).isPresent());

                assertTrue("should be registered in study", handle.attach(JdbiUserStudyEnrollment.class)
                        .getEnrollmentStatusByUserAndStudyGuids(userGuid, testData.getStudyGuid())
                        .isPresent());

                assertEquals("should assign invitation to created user",
                        (Long) createdUser.getId(), handle.attach(InvitationDao.class)
                                .findByInvitationGuid(testData.getStudyId(), invitation.getInvitationGuid())
                                .get().getUserId());
            });
        } finally {
            TransactionWrapper.useTxn(handle -> {
                handle.attach(InvitationSql.class).deleteById(invitation.getInvitationId());
                if (createdUserGuid.get() != null) {
                    String userGuid = createdUserGuid.get();
                    handle.attach(JdbiUserStudyEnrollment.class).deleteByUserGuidStudyGuid(userGuid, testData.getStudyGuid());
                    handle.attach(UserProfileSql.class).deleteByUserGuid(userGuid);
                    handle.execute("delete from user where guid = ?", userGuid);
                }
            });
        }
    }
}

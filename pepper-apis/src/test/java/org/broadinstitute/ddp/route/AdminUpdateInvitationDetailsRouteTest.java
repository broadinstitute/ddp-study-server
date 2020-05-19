package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.AuthDao;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.json.admin.UpdateInvitationDetailsPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class AdminUpdateInvitationDetailsRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String urlTemplate;

    @BeforeClass
    public static void setupData() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.ADMIN_STUDY_INVITATION_DETAILS
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
    public void testNotStudyAdmin() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(AuthDao.class).removeAdminFromAllStudies(testData.getUserId());
        });
        var payload = new UpdateInvitationDetailsPayload("foobar", "notes notes");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().patch(urlTemplate)
                .then().assertThat()
                .statusCode(401);
    }

    @Test
    public void testInvitationNotFound() {
        var payload = new UpdateInvitationDetailsPayload("foobar", "notes notes");
        given().auth().oauth2(testData.getTestingUser().getToken())
                .pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testNotesAreUpdated() {
        var invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new UpdateInvitationDetailsPayload(invitation.getInvitationGuid(), "notes notes");
            given().auth().oauth2(testData.getTestingUser().getToken())
                    .pathParam("study", testData.getStudyGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat()
                    .statusCode(200);
            TransactionWrapper.useTxn(handle -> {
                var actual = handle.attach(InvitationDao.class)
                        .findByInvitationGuid(testData.getStudyId(), invitation.getInvitationGuid());
                assertEquals("notes notes", actual.get().getNotes());
            });
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }
}

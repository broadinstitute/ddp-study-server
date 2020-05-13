package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;

import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.json.UpdateInvitationPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class UpdateInvitationRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String urlTemplate;

    @BeforeClass
    public static void setupData() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.INVITATION
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}")
                .replace(RouteConstants.PathParam.INVITATION_ID, "{invite}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
    }

    @Test
    public void testInvitationNotFound() {
        var payload = new UpdateInvitationPayload("notes notes");
        given().pathParam("study", testData.getStudyGuid())
                .pathParam("invite", "foobar")
                .body(payload, ObjectMapperType.GSON)
                .when().patch(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testNotesAreUpdated() {
        var invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new UpdateInvitationPayload("notes notes");
            given().pathParam("study", testData.getStudyGuid())
                    .pathParam("invite", invitation.getInvitationGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().patch(urlTemplate)
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

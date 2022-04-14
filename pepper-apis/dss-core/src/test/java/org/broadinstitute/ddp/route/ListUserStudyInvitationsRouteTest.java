package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.time.Instant;

import io.restassured.http.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class ListUserStudyInvitationsRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String urlTemplate;
    private static String token;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
        token = testData.getTestingUser().getToken();
        String endpoint = RouteConstants.API.USER_STUDY_INVITES
                .replace(RouteConstants.PathParam.USER_GUID, "{user}")
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + endpoint;
    }

    @Test
    public void testInvitations() {
        var inviteCode = "foobar";
        var acceptedAt = "2020-07-01T20:16:04.174091Z";

        InvitationDto invite = TransactionWrapper.withTxn(handle -> {
            var invitation = handle.attach(InvitationFactory.class)
                    .createRecruitmentInvitation(testData.getStudyId(), inviteCode);
            var invitationDao = handle.attach(InvitationDao.class);
            invitationDao.assignAcceptingUser(invitation.getInvitationId(), testData.getUserId(), Instant.parse(acceptedAt));
            return invitationDao.findByInvitationGuid(invitation.getStudyId(), invitation.getInvitationGuid()).get();
        });

        try {
            given().auth().oauth2(token)
                    .pathParam("user", testData.getUserGuid())
                    .pathParam("study", testData.getStudyGuid())
                    .when().get(urlTemplate).then()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("$.size()", equalTo(1))
                    .body("[0].invitationId", equalTo(inviteCode))
                    .body("[0].acceptedAt", equalTo(acceptedAt));
        } finally {
            TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                    .deleteById(invite.getInvitationId()));
        }
    }
}

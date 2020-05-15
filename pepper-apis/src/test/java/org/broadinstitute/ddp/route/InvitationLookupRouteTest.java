package org.broadinstitute.ddp.route;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.time.Instant;

import io.restassured.http.ContentType;
import io.restassured.mapper.ObjectMapperType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dao.InvitationSql;
import org.broadinstitute.ddp.json.InvitationLookupPayload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class InvitationLookupRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String urlTemplate;

    @BeforeClass
    public static void setupData() {
        urlTemplate = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.INVITATION_LOOKUP
                .replace(RouteConstants.PathParam.STUDY_GUID, "{study}");
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
        });
    }

    @Test
    public void testInvitationNotFound() {
        var payload = new InvitationLookupPayload("foobar");
        given().pathParam("study", testData.getStudyGuid())
                .body(payload, ObjectMapperType.GSON)
                .when().post(urlTemplate)
                .then().assertThat()
                .statusCode(404);
    }

    @Test
    public void testNoAcceptedUserYet() {
        var invitation = TransactionWrapper.withTxn(handle -> handle.attach(InvitationFactory.class)
                .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis()));
        try {
            var payload = new InvitationLookupPayload(invitation.getInvitationGuid());
            given().pathParam("study", testData.getStudyGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("invitationId", equalTo(invitation.getInvitationGuid()))
                    .body("createdAt", not(empty()))
                    .body("userGuid", nullValue());
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }

    @Test
    public void testHasAcceptedUser() {
        var invitation = TransactionWrapper.withTxn(handle -> {
            var invite = handle.attach(InvitationFactory.class)
                    .createRecruitmentInvitation(testData.getStudyId(), "invite" + System.currentTimeMillis());
            handle.attach(InvitationDao.class)
                    .assignAcceptingUser(invite.getInvitationId(), testData.getUserId(), Instant.now());
            return invite;
        });
        try {
            var payload = new InvitationLookupPayload(invitation.getInvitationGuid());
            given().pathParam("study", testData.getStudyGuid())
                    .body(payload, ObjectMapperType.GSON)
                    .when().post(urlTemplate)
                    .then().assertThat()
                    .statusCode(200).contentType(ContentType.JSON)
                    .body("invitationId", equalTo(invitation.getInvitationGuid()))
                    .body("createdAt", not(empty()))
                    .body("acceptedAt", not(empty()))
                    .body("userGuid", equalTo(testData.getUserGuid()))
                    .body("userHruid", equalTo(testData.getUserHruid()))
                    .body("userLoginEmail", equalTo(testData.getTestingUser().getEmail()));
        } finally {
            if (invitation != null) {
                TransactionWrapper.useTxn(handle -> handle.attach(InvitationSql.class)
                        .deleteById(invitation.getInvitationId()));
            }
        }
    }
}

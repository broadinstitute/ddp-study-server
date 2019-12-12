package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.Timestamp;

import com.google.gson.Gson;
import okhttp3.HttpUrl;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.InvitationDao;
import org.broadinstitute.ddp.db.dao.InvitationFactory;
import org.broadinstitute.ddp.db.dto.InvitationDto;
import org.broadinstitute.ddp.json.invitation.InvitationPayload;
import org.broadinstitute.ddp.model.invitation.InvitationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyInvitationRouteTest extends IntegrationTestSuite.TestCase {

    private static final Logger LOG = LoggerFactory.getLogger(VerifyInvitationRouteTest.class);

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Gson gson = new Gson();
    private static InvitationDto invitation;

    @BeforeClass
    public static void createInvitation() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);

            invitation = handle.attach(InvitationFactory.class).createInvitation(InvitationType.AGE_UP,
                    testData.getStudyId(),
                    testData.getUserId(),
                    "test" + System.currentTimeMillis() + "@datadonationplatform.org");
        });

    }

    @Before
    public void clearDates() {
        TransactionWrapper.useTxn(handle -> {
            handle.attach(InvitationDao.class).clearDates(invitation.getInvitationGuid());
        });
    }

    private String buildInvitationVerificationUrl() {
        String url = RouteTestUtil.getTestingBaseUrl() + RouteConstants.API.VERIFY_INVITATION;
        return HttpUrl.parse(url).newBuilder().build().toString();
    }

    @Test
    public void testVerificationOfGoodInvitation() throws Exception {
        var invitationPayload = new InvitationPayload(invitation.getInvitationGuid());
        Response response
                = Request.Post(buildInvitationVerificationUrl()).bodyString(gson.toJson(invitationPayload), ContentType.APPLICATION_JSON)
                .execute();

        assertEquals("Verification of invite failed",
                HttpStatus.SC_OK, response.returnResponse().getStatusLine().getStatusCode());

        TransactionWrapper.useTxn(handle -> {
            InvitationDto requeriedInvitation = handle.attach(InvitationDao.class).findByInvitationGuid(invitation.getGuid()).get();
            assertNotNull(requeriedInvitation.getVerifiedAt());
            assertTrue(requeriedInvitation.getCreatedAt().before(new Timestamp(System.currentTimeMillis())));
        });
    }

    @Test
    public void testVerificationReturns200ForBogusInvitation() throws Exception {
        var invitationPayload = new InvitationPayload("no one expects the spanish inquisitions");
        Response response
                = Request.Post(buildInvitationVerificationUrl()).bodyString(gson.toJson(invitationPayload), ContentType.APPLICATION_JSON)
                .execute();

        assertEquals("Verification of bogus invite failed",
                HttpStatus.SC_OK, response.returnResponse().getStatusLine().getStatusCode());
    }

    @Test
    public void testVerificationReturns200ForAlreadyVoidedInvitation() throws Exception {
        var invitationPayload = new InvitationPayload(invitation.getInvitationGuid());
        Timestamp voidedAt = new Timestamp(System.currentTimeMillis());

        TransactionWrapper.useTxn(handle -> {
            handle.attach(InvitationDao.class).updateVoidedAt(voidedAt, invitation.getInvitationGuid());
        });
        Response response
                = Request.Post(buildInvitationVerificationUrl()).bodyString(gson.toJson(invitationPayload), ContentType.APPLICATION_JSON)
                .execute();

        assertEquals("Verification failed",
                HttpStatus.SC_OK, response.returnResponse().getStatusLine().getStatusCode());

        TransactionWrapper.useTxn(handle -> {
            var queriedInvitation = handle.attach(InvitationDao.class).findByInvitationGuid(invitation.getInvitationGuid()).get();
            assertNull(queriedInvitation.getVerifiedAt());
            assertNotNull(queriedInvitation.getCreatedAt());
            assertNull(queriedInvitation.getAcceptedAt());
            assertNotNull(queriedInvitation.getVoidedAt());
        });
    }
}

package org.broadinstitute.dsm;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DDPRequestUtil;
import org.broadinstitute.lddp.handlers.util.FollowUpSurvey;
import org.broadinstitute.lddp.handlers.util.SimpleFollowUpSurvey;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.JsonBody;


@Ignore
public class DDPRequestUtilTest extends TestHelper {


    @BeforeClass
    public static void first() throws Exception {
        setupDB(true);
        startMockServer();
        setupUtils();
        DBTestUtil.makeTestDDPInstanceActive(true);
    }

    @AfterClass
    public static void last() {
        stopMockServer();
    }

    @Ignore("Broken!")
    @Test
    public void triggerFollowUpSurvey() throws Exception {
        mockDDP.when(
                        request().withMethod("POST").withPath("/ddp/followupsurvey/test-consent").withBody(
                                JsonBody.json("{\"participantId\": \"SURVEY_PARTICIPANT\"}",
                                        MatchType.STRICT
                                )
                        ))
                .respond(response().withStatusCode(200));

        FollowUpSurvey survey = new FollowUpSurvey("SURVEY_PARTICIPANT");

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(TEST_DDP);
        String sendRequest = ddpInstance.getBaseUrl() + "/ddp/followupsurvey/" + "test-consent";

        Integer ddpResponse = DDPRequestUtil.postRequest(sendRequest, survey, ddpInstance.getName(), ddpInstance.isHasAuth0Token());

        Assert.assertTrue(404 == ddpResponse);
    }

    @Ignore("Broken. Counting on having a running server might not be a good idea")
    @Test
    public void triggerSimpleFollowUpSurvey() throws Exception {
        mockDDP.when(
                        request().withMethod("POST").withPath("/ddp/followupsurvey/test-consent").withBody(
                                JsonBody.json("{\"participantId\": \"SURVEY_PARTICIPANT\"}",
                                        MatchType.STRICT
                                )
                        ))
                .respond(response().withStatusCode(200));

        SimpleFollowUpSurvey survey = new SimpleFollowUpSurvey("SURVEY_PARTICIPANT");

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(TEST_DDP);
        String sendRequest = ddpInstance.getBaseUrl() + "/ddp/followupsurvey/" + "test-consent";

        Integer ddpResponse = DDPRequestUtil.postRequest(sendRequest, survey, ddpInstance.getName(), ddpInstance.isHasAuth0Token());

        Assert.assertTrue(200 == ddpResponse);
    }
}

package org.broadinstitute.ddp.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.util.RouteUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.junit.MockServerRule;
import org.mockserver.model.Header;
import org.mockserver.verify.VerificationTimes;

public class DsmClientTest {

    private static Gson gson = new Gson();

    @Rule
    public MockServerRule mockServer = new MockServerRule(this, true);  // use global instance to speed up tests

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    private DsmClient dsm;

    @Before
    public void setup() {
        String dsmBaseUrl = "http://localhost:" + mockServer.getPort();
        dsm = new DsmClient(dsmBaseUrl, "fake-secret", "signer");
    }

    @After
    public void reset() {
        mockServer.getClient().reset();
    }

    @Test
    public void testListCancers() {
        var sampleCancerList = new String[] {"Cancer1", "Cancer2", "Cancer3", "Cancer4"};

        mockServer.getClient()
                .when(request().withMethod("GET").withPath(DsmClient.API_CANCERS))
                .respond(response().withStatusCode(200).withBody(gson.toJson(sampleCancerList)));

        ClientResponse<List<String>> resp = dsm.listCancers();
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertArrayEquals(sampleCancerList, resp.getBody().toArray(new String[] {}));
    }

    @Test
    public void testListCancers_errorStatusReturned() {
        mockServer.getClient()
                .when(request().withMethod("GET").withPath(DsmClient.API_CANCERS))
                .respond(response().withStatusCode(500));

        ClientResponse<List<String>> resp = dsm.listCancers();
        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }

    @Test
    public void testListDrugs() {
        var sampleDrugList = new String[] {"Drug1", "Drug2", "Drug3", "Drug4"};

        mockServer.getClient()
                .when(request().withMethod("GET").withPath(DsmClient.API_DRUGS))
                .respond(response().withStatusCode(200).withBody(gson.toJson(sampleDrugList)));

        ClientResponse<List<String>> resp = dsm.listDrugs();
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertArrayEquals(sampleDrugList, resp.getBody().toArray(new String[] {}));
    }

    @Test
    public void testListDrugs_errorStatusReturned() {
        mockServer.getClient()
                .when(request().withMethod("GET").withPath(DsmClient.API_DRUGS))
                .respond(response().withStatusCode(500));

        ClientResponse<List<String>> resp = dsm.listDrugs();
        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }

    @Test
    public void testGetParticipantStatus() {
        var sampleStatus = new ParticipantStatus(
                1547890240L,
                1549890640L,
                1552309836L,
                1553519436L,
                1556519825L,
                Collections.emptyList());

        String expectedPath = DsmClient.API_PARTICIPANT_STATUS
                .replace(RouteConstants.PathParam.STUDY_GUID, "abc")
                .replace(RouteConstants.PathParam.USER_GUID, "xyz");
        mockServer.getClient()
                .when(request().withMethod("GET").withPath(expectedPath))
                .respond(response().withStatusCode(200).withBody(gson.toJson(sampleStatus)));

        ClientResponse<ParticipantStatus> resp = dsm.getParticipantStatus("abc", "xyz", "token");
        assertNotNull(resp);
        assertEquals(200, resp.getStatusCode());

        ParticipantStatus actual = resp.getBody();
        assertNotNull(actual);
        assertEquals(sampleStatus.getMrRequestedEpochTimeSec(), actual.getMrRequestedEpochTimeSec());
        assertEquals(sampleStatus.getMrReceivedEpochTimeSec(), actual.getMrReceivedEpochTimeSec());
        assertEquals(sampleStatus.getTissueRequestedEpochTimeSec(), actual.getTissueRequestedEpochTimeSec());
        assertEquals(sampleStatus.getTissueReceivedEpochTimeSec(), actual.getTissueReceivedEpochTimeSec());
        assertEquals(sampleStatus.getTissueSentEpochTimeSec(), actual.getTissueSentEpochTimeSec());

        var expectedHeader = new Header(RouteConstants.AUTHORIZATION, RouteUtil.makeAuthBearerHeader("token"));
        mockServer.getClient().verify(request().withHeader(expectedHeader), VerificationTimes.exactly(1));
    }

    @Test
    public void testGetParticipantStatus_errorStatusReturned() {
        String expectedPath = DsmClient.API_PARTICIPANT_STATUS
                .replace(RouteConstants.PathParam.STUDY_GUID, "abc")
                .replace(RouteConstants.PathParam.USER_GUID, "xyz");
        mockServer.getClient()
                .when(request().withMethod("GET").withPath(expectedPath))
                .respond(response().withStatusCode(500));

        ClientResponse<ParticipantStatus> resp = dsm.getParticipantStatus("abc", "xyz", "token");
        assertNotNull(resp);
        assertEquals(500, resp.getStatusCode());
    }
}

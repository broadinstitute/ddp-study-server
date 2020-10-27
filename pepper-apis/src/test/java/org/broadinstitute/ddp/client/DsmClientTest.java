package org.broadinstitute.ddp.client;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLSession;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.model.dsm.ParticipantKits;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.RouteUtil;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

public class DsmClientTest {

    private static Gson gson = new Gson();
    private static Config cfg;

    private HttpClient mockHttp;
    private DsmClient dsm;

    @BeforeClass
    public static void setup() {
        cfg = ConfigManager.getInstance().getConfig();
    }

    @Before
    public void init() {
        mockHttp = mock(HttpClient.class);
        dsm = new DsmClient(cfg, mockHttp);
    }

    @Test
    public void testListCancers() throws IOException, InterruptedException {
        var sampleCancerList = new String[] {"Cancer1", "Cancer2", "Cancer3", "Cancer4"};

        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(200, gson.toJson(sampleCancerList)));
        var result = dsm.listCancers();

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());
        assertArrayEquals(sampleCancerList, result.getBody().toArray(new String[] {}));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttp, times(1)).send(captor.capture(), any());

        var actualRequest = captor.getValue();
        assertEquals("GET", actualRequest.method());
        assertEquals(DsmClient.PATH_CANCERS, actualRequest.uri().getPath());
    }

    @Test
    public void testListCancers_errorStatusReturned() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(400, null));
        var result = dsm.listCancers();
        assertNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(400, result.getStatusCode());

        reset(mockHttp);
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(500, null));
        result = dsm.listCancers();
        assertEquals(500, result.getStatusCode());
    }

    @Test
    public void testListCancers_exception() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenThrow(new IOException("from test"));
        var result = dsm.listCancers();

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNotNull(result.getThrown());
        assertEquals(500, result.getStatusCode());
        assertEquals("from test", result.getThrown().getMessage());
    }

    @Test
    public void testListDrugs() throws IOException, InterruptedException {
        var sampleDrugList = new String[] {"Drug1", "Drug2", "Drug3", "Drug4"};

        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(200, gson.toJson(sampleDrugList)));
        var result = dsm.listDrugs();

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());
        assertArrayEquals(sampleDrugList, result.getBody().toArray(new String[] {}));

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttp, times(1)).send(captor.capture(), any());

        var actualRequest = captor.getValue();
        assertEquals("GET", actualRequest.method());
        assertEquals(DsmClient.PATH_DRUGS, actualRequest.uri().getPath());
    }

    @Test
    public void testListDrugs_errorStatusReturned() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(400, null));
        var result = dsm.listDrugs();
        assertNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(400, result.getStatusCode());

        reset(mockHttp);
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(500, null));
        result = dsm.listDrugs();
        assertEquals(500, result.getStatusCode());
    }

    @Test
    public void testListDrugs_exception() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenThrow(new IOException("from test"));
        var result = dsm.listDrugs();

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNotNull(result.getThrown());
        assertEquals(500, result.getStatusCode());
        assertEquals("from test", result.getThrown().getMessage());
    }

    @Test
    public void testListParticipantKits() throws IOException, InterruptedException {
        var fakeKits = List.of(new ParticipantKits("user1", List.of()));

        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(200, gson.toJson(fakeKits)));
        var result = dsm.listParticipantKits("study1", List.of("user1", "user2"));

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());
        assertEquals(1, result.getBody().size());
        assertEquals("user1", result.getBody().get(0).getParticipantGuid());

        String expectedPath = DsmClient.PATH_BATCH_KITS_STATUS
                .replace(RouteConstants.PathParam.STUDY_GUID, "study1");
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttp, times(1)).send(captor.capture(), any());

        var actualRequest = captor.getValue();
        assertEquals("POST", actualRequest.method());
        assertEquals(expectedPath, actualRequest.uri().getPath());
    }

    @Test
    public void testPaginateParticipantKits() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(200, gson.toJson(List.of())));

        int batchSize = 1;
        var numProcessed = dsm.paginateParticipantKits(batchSize, "study1", List.of("user1", "user2"), (subset, page) -> true);

        assertEquals(2, numProcessed);
        verify(mockHttp, times(2)).send(any(), any());
    }

    @Test
    public void testGetParticipantStatus() throws IOException, InterruptedException {
        var sampleStatus = new ParticipantStatus(
                1547890240L,
                1549890640L,
                1552309836L,
                1553519436L,
                1556519825L,
                Collections.emptyList());

        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(200, gson.toJson(sampleStatus)));
        var result = dsm.getParticipantStatus("abc", "xyz", "token");

        assertNotNull(result);
        assertNotNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(200, result.getStatusCode());

        ParticipantStatus actual = result.getBody();
        assertNotNull(actual);
        assertEquals(sampleStatus.getMrRequestedEpochTimeSec(), actual.getMrRequestedEpochTimeSec());
        assertEquals(sampleStatus.getMrReceivedEpochTimeSec(), actual.getMrReceivedEpochTimeSec());
        assertEquals(sampleStatus.getTissueRequestedEpochTimeSec(), actual.getTissueRequestedEpochTimeSec());
        assertEquals(sampleStatus.getTissueReceivedEpochTimeSec(), actual.getTissueReceivedEpochTimeSec());
        assertEquals(sampleStatus.getTissueSentEpochTimeSec(), actual.getTissueSentEpochTimeSec());

        String expectedPath = DsmClient.PATH_PARTICIPANT_STATUS
                .replace(RouteConstants.PathParam.STUDY_GUID, "abc")
                .replace(RouteConstants.PathParam.USER_GUID, "xyz");
        var expectedAuth = RouteUtil.makeAuthBearerHeader("token");

        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(mockHttp, times(1)).send(captor.capture(), any());

        var actualRequest = captor.getValue();
        assertEquals("GET", actualRequest.method());
        assertEquals(expectedPath, actualRequest.uri().getPath());
        assertEquals(expectedAuth, actualRequest.headers().firstValue(RouteConstants.Header.AUTHORIZATION).orElse(null));
    }

    @Test
    public void testGetParticipantStatus_errorStatusReturned() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(400, null));
        var result = dsm.getParticipantStatus("abc", "xyz", "token");
        assertNull(result.getBody());
        assertNull(result.getError());
        assertNull(result.getThrown());
        assertEquals(400, result.getStatusCode());

        reset(mockHttp);
        when(mockHttp.send(any(), any())).thenReturn(TestResponse.of(500, null));
        result = dsm.getParticipantStatus("abc", "xyz", "token");
        assertEquals(500, result.getStatusCode());
    }

    @Test
    public void testGetParticipantStatus_exception() throws IOException, InterruptedException {
        when(mockHttp.send(any(), any())).thenThrow(new IOException("from test"));
        var result = dsm.getParticipantStatus("abc", "xyz", "token");

        assertNull(result.getBody());
        assertNull(result.getError());
        assertNotNull(result.getThrown());
        assertEquals(500, result.getStatusCode());
        assertEquals("from test", result.getThrown().getMessage());
    }

    static class TestResponse<T> implements HttpResponse<T> {

        private final int statusCode;
        private final T body;

        public static <T> TestResponse<T> of(int statusCode, T body) {
            return new TestResponse<>(statusCode, body);
        }

        public TestResponse(int statusCode, T body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public T body() {
            return body;
        }

        @Override
        public HttpRequest request() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HttpResponse<T>> previousResponse() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpHeaders headers() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<SSLSession> sslSession() {
            throw new UnsupportedOperationException();
        }

        @Override
        public URI uri() {
            throw new UnsupportedOperationException();
        }

        @Override
        public HttpClient.Version version() {
            throw new UnsupportedOperationException();
        }
    }
}

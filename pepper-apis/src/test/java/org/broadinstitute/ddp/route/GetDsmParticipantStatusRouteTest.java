package org.broadinstitute.ddp.route;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo.RecordStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.index.get.GetResult;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.HaltException;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class GetDsmParticipantStatusRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static final Gson gson = new Gson();
    private static Config cfg;

    private GetDsmParticipantStatusRoute route;
    private RestHighLevelClient mockESClient;

    @BeforeClass
    public static void setup() {
        cfg = RouteTestUtil.getConfig();
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.ENROLLED);
        });
    }

    @AfterClass
    public static void tearDown() {
        TransactionWrapper.useTxn(handle -> TestDataSetupUtil.deleteStudyEnrollmentStatuses(handle, testData));
    }

    @Before
    public void init() {
        mockESClient = mock(RestHighLevelClient.class);
        route = new GetDsmParticipantStatusRoute(mockESClient);
    }

    @Test
    public void testProcess_returnStatusInfo() throws IOException {
        GetResponse getResponse = new GetResponse(new GetResult("index", "id", "id01", 0,
                1, 1, true,
                new BytesArray("{\"workflows\":[{\"workflow\":\"ACCEPTANCE_STATUS\",\"status\":\"ACCEPTED\"}],"
                        + "\"samples\": [{\"trackingIn\": \"testtrackingIn\",\"kitType\": \"testType\",\"carrier\": "
                        + "\"testCarrier\",\"kitRequestId\": \"5729\",\"trackingOut\": \"testtrackingOut\",\"delivered\":"
                        + " \"2020-2-29\",\"received\": \"2020-2-29\",\"sent\": \"2020-2-29\"}],"
                        + " \"dsm\":{\"medicalRecords\":[{\"requested\":\"2019-12-27\",\"name\":\"TestValue1\","
                        + "\"received\":\"2019-12-28\",\"type\":\"testType\",\"medicalRecordId\":5729}],\"tissueRecords\""
                        + ":[{\"requested\":\"2020-2-28\",\"histology\":\"testType\",\"datePx\":\"2020-2-28\","
                        + "\"received\":\"2020-03-10\",\"locationPx\":\"testLocation\",\"typePx\":\"testType\","
                        + "\"sent\":\"2020-2-29\",\"accessionNumber\":\"423423233232\",\"tissueRecordsId\":5729}]}}"),
                null));

        when(mockESClient.get(any(GetRequest.class), eq(RequestOptions.DEFAULT)))
                .thenReturn(getResponse);

        ParticipantStatusTrackingInfo actual = route.process(testData.getStudyGuid(), testData.getUserGuid());

        assertNotNull(actual);
        assertEquals(testData.getUserGuid(), actual.getUserGuid());
        assertEquals(RecordStatus.RECEIVED, actual.getMedicalRecords().get(0).getStatus());
        assertEquals("2019-12-27", actual.getMedicalRecords().get(0).getRequestedAt());
        assertEquals("2019-12-28", actual.getMedicalRecords().get(0).getReceivedBackAt());
        assertEquals(RecordStatus.RECEIVED, actual.getTissueRecords().get(0).getStatus());
        assertEquals("2020-2-28", actual.getTissueRecords().get(0).getRequestedAt());
        assertEquals("2020-03-10", actual.getTissueRecords().get(0).getReceivedBackAt());
        assertNotNull(actual.getWorkflows());
        assertEquals(1, actual.getWorkflows().size());
        assertEquals("ACCEPTANCE_STATUS", actual.getWorkflows().get(0).getWorkflow());
        assertEquals("ACCEPTED", actual.getWorkflows().get(0).getStatus());
    }

    @Test
    public void testProcess_whenStudyNotExist_then404() {
        try {
            route.process("abcxyz", testData.getUserGuid());
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(404, halted.statusCode());
            assertNotNull(halted.body());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.NOT_FOUND, err.getCode());
            assertTrue(err.getMessage().contains("study abcxyz"));
        }
    }

    @Test
    public void testProcess_whenUserNotExist_then404() {
        try {
            route.process(testData.getUserGuid(), "abcxyz");
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(404, halted.statusCode());
            assertNotNull(halted.body());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.NOT_FOUND, err.getCode());
            assertTrue(err.getMessage().contains("Participant abcxyz"));
        }
    }

    @Test
    public void testProcess_whenUserNotInStudy_then404() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            try {
                route.process(study2.getGuid(), testData.getUserGuid());
                fail("expected exception not thrown");
            } catch (HaltException halted) {
                assertEquals(404, halted.statusCode());
                assertNotNull(halted.body());
                var err = gson.fromJson(halted.body(), ApiError.class);
                assertEquals(ErrorCodes.NOT_FOUND, err.getCode());
                assertTrue(err.getMessage().contains(testData.getUserGuid()));
                assertTrue(err.getMessage().contains(study2.getGuid()));
            }
            handle.rollback();
        });
    }

    @Test
    public void testProcess_whenClientErrors_then500() throws IOException {
        when(mockESClient.get(any(GetRequest.class), eq(RequestOptions.DEFAULT)))
                .thenThrow(new IOException());
        try {
            route.process(testData.getStudyGuid(), testData.getUserGuid());
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(500, halted.statusCode());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
        }
        try {
            route.process(testData.getStudyGuid(), testData.getUserGuid());
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(500, halted.statusCode());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
        }
    }
}

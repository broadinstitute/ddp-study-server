package org.broadinstitute.ddp.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.ClientResponse;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo.RecordStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.HaltException;

public class GetDsmParticipantStatusRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Gson gson = new Gson();
    private static Config cfg;

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

    @Test
    public void testProcess_returnStatusInfo() {
        var expected = new ParticipantStatus(1L, 2L, 3L, 4L, 5L, List.of(
                new ParticipantStatus.Sample("BLOOD", 6L, 7L, 8L, "tracking9", "carrier10")));

        DsmClient mockClient = mock(DsmClient.class);
        when(mockClient
                .getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(new ClientResponse<>(200, expected));

        var route = new GetDsmParticipantStatusRoute(mockClient);
        ParticipantStatusTrackingInfo actual = route.process(testData.getStudyGuid(), testData.getUserGuid(), "token");

        assertNotNull(actual);
        assertEquals(testData.getUserGuid(), actual.getUserGuid());
        assertEquals(RecordStatus.RECEIVED, actual.getMedicalRecord().getStatus());
        assertEquals(expected.getMrRequestedEpochTimeSec(), actual.getMedicalRecord().getRequestedAt());
        assertEquals(expected.getMrReceivedEpochTimeSec(), actual.getMedicalRecord().getReceivedBackAt());
        assertEquals(RecordStatus.RECEIVED, actual.getTissueRecord().getStatus());
        assertEquals(expected.getTissueRequestedEpochTimeSec(), actual.getTissueRecord().getRequestedAt());
        assertEquals(expected.getTissueReceivedEpochTimeSec(), actual.getTissueRecord().getReceivedBackAt());
    }

    @Test
    public void testProcess_whenStudyNotExist_then404() {
        try {
            var route = new GetDsmParticipantStatusRoute(new DsmClient(cfg));
            route.process("abcxyz", testData.getUserGuid(), "token");
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
            var route = new GetDsmParticipantStatusRoute(new DsmClient(cfg));
            route.process(testData.getUserGuid(), "abcxyz", "token");
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
                var route = new GetDsmParticipantStatusRoute(new DsmClient(cfg));
                route.process(study2.getGuid(), testData.getUserGuid(), "token");
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
    public void testProcess_whenClientErrors_then500() {
        DsmClient mockClient = mock(DsmClient.class);
        when(mockClient
                .getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(new ClientResponse<>(403, null));

        var route = new GetDsmParticipantStatusRoute(mockClient);

        try {
            route.process(testData.getStudyGuid(), testData.getUserGuid(), "token");
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(500, halted.statusCode());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
        }

        reset(mockClient);
        when(mockClient
                .getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(new ClientResponse<>(502, null));

        try {
            route.process(testData.getStudyGuid(), testData.getUserGuid(), "token");
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(500, halted.statusCode());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
        }
    }
}

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
import org.broadinstitute.ddp.client.ApiResult;
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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import spark.HaltException;

public class GetDsmParticipantStatusRouteTest extends IntegrationTestSuite.TestCase {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Gson gson = new Gson();
    private static Config cfg;

    private DsmClient mockDsm;
    private GetDsmParticipantStatusRoute route;

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
        mockDsm = mock(DsmClient.class);
        route = new GetDsmParticipantStatusRoute(mockDsm);
    }

    @Test
    public void testProcess_returnStatusInfo() {
        var expected = new ParticipantStatus(1L, 2L, 3L, 4L, 5L, List.of(
                new ParticipantStatus.Sample("a", "BLOOD", 6L, 7L, 8L, "tracking9", "carrier10")));

        when(mockDsm.getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(ApiResult.ok(200, expected));

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
        when(mockDsm.getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(ApiResult.err(403, null));

        try {
            route.process(testData.getStudyGuid(), testData.getUserGuid(), "token");
            fail("expected exception not thrown");
        } catch (HaltException halted) {
            assertEquals(500, halted.statusCode());
            var err = gson.fromJson(halted.body(), ApiError.class);
            assertEquals(ErrorCodes.SERVER_ERROR, err.getCode());
        }

        reset(mockDsm);
        when(mockDsm.getParticipantStatus(testData.getStudyGuid(), testData.getUserGuid(), "token"))
                .thenReturn(ApiResult.err(502, null));

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

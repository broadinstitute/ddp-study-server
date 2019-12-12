package org.broadinstitute.ddp.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.gson.Gson;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.dsm.DsmCallResponse;
import org.broadinstitute.ddp.model.dsm.ParticipantStatus;
import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import org.mockserver.junit.MockServerRule;

public class DsmParticipantStatusServiceTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static final String URL_REGEX = "^/info/participantstatus/.+";
    private static URL dsmBaseUrl;

    static {
        String dsmBaseUrl = ConfigManager.getInstance().getConfig().getString(ConfigFile.DSM_BASE_URL);
        try {
            DsmParticipantStatusServiceTest.dsmBaseUrl = new URL(dsmBaseUrl);
        } catch (MalformedURLException e) {
            throw new DDPException("Could not run test because " + dsmBaseUrl + " is not a valid URL.");
        }

    }

    private static DsmParticipantStatusService service;

    @Rule
    public MockServerRule mockServerRule = new MockServerRule(this, false, dsmBaseUrl.getPort());


    @BeforeClass
    public static void setupClass() {
        service = new DsmParticipantStatusService(dsmBaseUrl);
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    private long addUserStudyEnrollmentRecord(EnrollmentStatusType status) {
        return TransactionWrapper.withTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(),
                    testData.getStudyGuid(),
                    status
                )
        );
    }

    private void deleteUserStudyEnrollmentRecord(long id) {
        TransactionWrapper.useTxn(
                handle -> handle.attach(JdbiUserStudyEnrollment.class).deleteById(id)
        );
    }

    private DsmCallResponse fetchParticipantStatus() {
        return service.fetchParticipantStatus(
                testData.getStudyGuid(),
                testData.getUserGuid(),
                TestData.DSM_TOKEN
        );
    }

    @Test
    public void test_givenStudyAndUserExistInDsm_whenServiceIsCalled_thenItReturnsValidDto() {
        long userEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS)
            );
            ParticipantStatusTrackingInfo status = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(TestData.MR_REQUESTED_TIMESTAMP, status.getMedicalRecord().getRequestedAt().longValue());
            Assert.assertEquals(TestData.MR_RECEIVED_TIMESTAMP, status.getMedicalRecord().getReceivedBackAt().longValue());
            Assert.assertEquals(TestData.TISSUE_REQUESTED_TIMESTAMP, status.getTissueRecord().getRequestedAt().longValue());
            Assert.assertEquals(TestData.TISSUE_RECEIVED_TIMESTAMP, status.getTissueRecord().getReceivedBackAt().longValue());
        } finally {
            deleteUserStudyEnrollmentRecord(userEnrollmentId);
        }
    }

    @Test
    public void test_givenStudyDoesntExistInDsm_whenServiceIsCalled_thenItReturns404() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 404, "");
            Assert.assertEquals(
                    404,
                    fetchParticipantStatus().getHttpStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserDoesNotExistInPepper_whenServiceIsCalled_thenItReturns404() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 404, "");
            Assert.assertEquals(
                    404,
                    fetchParticipantStatus().getHttpStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsNotEnrolled_whenServiceIsCalled_thenItReturns500() {
        TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 200, "");
        Assert.assertEquals(
                500,
                fetchParticipantStatus().getHttpStatus()
        );
    }

    @Test
    public void test_givenTokenIsValid_whenDsmIsCalled_thenAuthorizationHeaderIsSetCorrectly() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(), URL_REGEX, 200, new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS)
            );
            fetchParticipantStatus();
            String headerContents = "Bearer " + TestData.DSM_TOKEN;
            TestUtil.verifyUrlAndHeader(mockServerRule.getPort(), "Authorization", headerContents);
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    public void test_givenDsmRespondsWith5xx_whenServiceReceivesIt_thenItRelaysIt() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 502, "");
            Assert.assertEquals(
                    502,
                    fetchParticipantStatus().getHttpStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    public void test_givenDsmRespondsWith4xx_whenServiceReceivesIt_thenItRelaysIt() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 422, "");
            Assert.assertEquals(
                    422,
                    fetchParticipantStatus().getHttpStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    public void test_givenDsmRespondsWithBrokenJson_whenServiceReceivesIt_thenItEmits500() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(mockServerRule.getPort(), URL_REGEX, 200, "[}");
            Assert.assertEquals(
                    500,
                    fetchParticipantStatus().getHttpStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsNotEnrolled_whenServiceIsCalled_thenStatusIsIneligible() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.REGISTERED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(), URL_REGEX, 200, new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS)
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.INELIGIBLE,
                    participantStatusTrackingInfo.getMedicalRecord().getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsEnrolledAndMrRequestedIsNullAndMrReceivedIsNull_whenServiceIsCalled_thenStatusIsPending() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_REQUESTED_RECEIVED)
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.PENDING,
                    participantStatusTrackingInfo.getMedicalRecord().getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsEnrolledAndMrRequestedIsNotNullAndMrReceivedIsNull_whenServiceIsCalled_thenStatusIsSent() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_RECEIVED)
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.SENT,
                    participantStatusTrackingInfo.getMedicalRecord().getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenEnrolledAndDeliveredIsNullAndReceivedIsNull_whenServiceIsCalled_thenKitStatusIsSent() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        ParticipantStatus.Sample sample = new ParticipantStatus.Sample(
                "saliva",
                TestData.KIT_SENT_TIMESTAMP,
                null,
                null,
                "AA-BB-CC-DD",
                "A carrier"
        );
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(
                        new ParticipantStatus(
                                TestData.MR_REQUESTED_TIMESTAMP,
                                TestData.MR_RECEIVED_TIMESTAMP,
                                TestData.TISSUE_REQUESTED_TIMESTAMP,
                                TestData.TISSUE_SENT_TIMESTAMP,
                                TestData.TISSUE_RECEIVED_TIMESTAMP,
                                new ArrayList<>(Arrays.asList(sample))
                        )
                    )
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.SENT,
                    participantStatusTrackingInfo.getKits().get(0).getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenEnrolledAndDeliveredIsNotNullAndReceivedIsNull_whenServiceIsCalled_thenKitStatusIsDelivered() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        ParticipantStatus.Sample sample = new ParticipantStatus.Sample(
                "saliva",
                TestData.KIT_SENT_TIMESTAMP,
                TestData.KIT_DELIVERED_TIMESTAMP,
                null,
                "AA-BB-CC-DD",
                "A carrier"
        );
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(
                        new ParticipantStatus(
                                TestData.MR_REQUESTED_TIMESTAMP,
                                TestData.MR_RECEIVED_TIMESTAMP,
                                TestData.TISSUE_REQUESTED_TIMESTAMP,
                                TestData.TISSUE_SENT_TIMESTAMP,
                                TestData.TISSUE_RECEIVED_TIMESTAMP,
                                new ArrayList<>(Arrays.asList(sample))
                        )
                    )
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.DELIVERED,
                    participantStatusTrackingInfo.getKits().get(0).getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenEnrolledAndDeliveredIsNotNullAndReceivedIsNotNull_whenServiceIsCalled_thenKitStatusIsReceived() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        ParticipantStatus.Sample sample = new ParticipantStatus.Sample(
                "saliva",
                TestData.KIT_SENT_TIMESTAMP,
                TestData.KIT_DELIVERED_TIMESTAMP,
                TestData.KIT_RECEIVED_TIMESTAMP,
                "AA-BB-CC-DD",
                "A carrier"
        );
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(
                        new ParticipantStatus(
                                TestData.MR_REQUESTED_TIMESTAMP,
                                TestData.MR_RECEIVED_TIMESTAMP,
                                TestData.TISSUE_REQUESTED_TIMESTAMP,
                                TestData.TISSUE_SENT_TIMESTAMP,
                                TestData.TISSUE_RECEIVED_TIMESTAMP,
                                new ArrayList<>(Arrays.asList(sample))
                        )
                    )
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.RECEIVED,
                    participantStatusTrackingInfo.getKits().get(0).getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenEnrolledAndDeliveredNotNull_ReceivedNotNull_TrackingIdNull_whenServiceIsCalled_thenKitStatusIsReceived() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        ParticipantStatus.Sample sample = new ParticipantStatus.Sample(
                "saliva",
                TestData.KIT_SENT_TIMESTAMP,
                null,
                TestData.KIT_RECEIVED_TIMESTAMP,
                null,
                "A carrier"
        );
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(
                        new ParticipantStatus(
                                TestData.MR_REQUESTED_TIMESTAMP,
                                TestData.MR_RECEIVED_TIMESTAMP,
                                TestData.TISSUE_REQUESTED_TIMESTAMP,
                                TestData.TISSUE_SENT_TIMESTAMP,
                                TestData.TISSUE_RECEIVED_TIMESTAMP,
                                new ArrayList<>(Arrays.asList(sample))
                        )
                    )
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.RECEIVED,
                    participantStatusTrackingInfo.getKits().get(0).getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsEnrolledAndDeliveredNullAndReceivedIsNotNull_whenServiceIsCalled_thenKitStatusIsUnknown() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        ParticipantStatus.Sample sample = new ParticipantStatus.Sample(
                "saliva",
                TestData.KIT_SENT_TIMESTAMP,
                null,
                TestData.KIT_DELIVERED_TIMESTAMP,
                "AA-BB-CC-DD",
                "A carrier"
        );
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(
                        new ParticipantStatus(
                                TestData.MR_REQUESTED_TIMESTAMP,
                                TestData.MR_RECEIVED_TIMESTAMP,
                                TestData.TISSUE_REQUESTED_TIMESTAMP,
                                TestData.TISSUE_SENT_TIMESTAMP,
                                TestData.TISSUE_RECEIVED_TIMESTAMP,
                                new ArrayList<>(Arrays.asList(sample))
                        )
                    )
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.RECEIVED,
                    participantStatusTrackingInfo.getKits().get(0).getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    public void test_givenUserIsEnrolledAndMrRequestedIsNotNullAndMrReceivedIsNotNull_whenServiceIsCalled_thenStatusIsReceived() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS)
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.RECEIVED,
                    participantStatusTrackingInfo.getMedicalRecord().getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    @Test
    // Weird, the request was never sent but was received
    public void test_givenUserIsEnrolledAndMrRequestedIsNullAndMrReceivedIsNotNull_whenServiceIsCalled_thenStatusIsUnknown() {
        long userStudyEnrollmentId = addUserStudyEnrollmentRecord(EnrollmentStatusType.ENROLLED);
        try {
            TestUtil.stubMockServerForRequest(
                    mockServerRule.getPort(),
                    URL_REGEX,
                    200,
                    new Gson().toJson(TestData.DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_REQUESTED)
            );
            ParticipantStatusTrackingInfo participantStatusTrackingInfo = fetchParticipantStatus().getParticipantStatusTrackingInfo();
            Assert.assertEquals(
                    ParticipantStatusTrackingInfo.RecordStatus.RECEIVED,
                    participantStatusTrackingInfo.getMedicalRecord().getStatus()
            );
        } finally {
            deleteUserStudyEnrollmentRecord(userStudyEnrollmentId);
        }
    }

    private static class TestData {
        public static final String NON_EXISTENT_STUDY_GUID = "NON_EXISTENT_STUDY";
        public static final ParticipantStatus DSM_PARTICIPANT_STATUS = new ParticipantStatus(
                TestData.MR_REQUESTED_TIMESTAMP,
                TestData.MR_RECEIVED_TIMESTAMP,
                TestData.TISSUE_REQUESTED_TIMESTAMP,
                TestData.TISSUE_SENT_TIMESTAMP,
                TestData.TISSUE_RECEIVED_TIMESTAMP,
                new ArrayList<>()
        );
        public static final ParticipantStatus DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_REQUESTED = new ParticipantStatus(
                null,
                TestData.MR_RECEIVED_TIMESTAMP,
                TestData.TISSUE_REQUESTED_TIMESTAMP,
                TestData.TISSUE_SENT_TIMESTAMP,
                TestData.TISSUE_RECEIVED_TIMESTAMP,
                new ArrayList<>()
        );
        public static final ParticipantStatus DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_RECEIVED = new ParticipantStatus(
                TestData.MR_REQUESTED_TIMESTAMP,
                null,
                TestData.TISSUE_REQUESTED_TIMESTAMP,
                TestData.TISSUE_SENT_TIMESTAMP,
                TestData.TISSUE_RECEIVED_TIMESTAMP,
                new ArrayList<>()
        );
        public static final ParticipantStatus DSM_PARTICIPANT_STATUS_WITH_MISSING_MR_REQUESTED_RECEIVED = new ParticipantStatus(
                null,
                null,
                TestData.TISSUE_REQUESTED_TIMESTAMP,
                TestData.TISSUE_SENT_TIMESTAMP,
                TestData.TISSUE_RECEIVED_TIMESTAMP,
                new ArrayList<>()
        );
        public static final long MR_REQUESTED_TIMESTAMP = 1547890240;
        public static final long MR_RECEIVED_TIMESTAMP = 1549890640;
        public static final long TISSUE_REQUESTED_TIMESTAMP = 1552309836;
        public static final long TISSUE_SENT_TIMESTAMP = 1553519436;
        public static final long TISSUE_RECEIVED_TIMESTAMP = 1556519825;
        public static final long KIT_SENT_TIMESTAMP = 1553519436;
        public static final long KIT_DELIVERED_TIMESTAMP = 1555615422;
        public static final long KIT_RECEIVED_TIMESTAMP = 1556519825;
        public static final String DSM_TOKEN = "aabbcc";
    }
}

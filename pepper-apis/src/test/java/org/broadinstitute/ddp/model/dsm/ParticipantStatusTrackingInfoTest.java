package org.broadinstitute.ddp.model.dsm;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo.RecordStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.junit.Test;

public class ParticipantStatusTrackingInfoTest {

    @Test
    public void testRecords_notEnrolled_thenIneligible() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, Collections.emptyList()),
                EnrollmentStatusType.REGISTERED, "guid");
        assertEquals(RecordStatus.INELIGIBLE, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.INELIGIBLE, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNoTimestamps_thenPending() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, Collections.emptyList()),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(RecordStatus.PENDING, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.PENDING, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequested_thenSent() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(1L, null, 2L, null, null, Collections.emptyList()),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(RecordStatus.SENT, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.SENT, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequestedAndReceived_thenReceived() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(1L, 2L, 3L, null, 4L, Collections.emptyList()),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNotRequestedButReceived_thenReceived() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, 1L, null, null, 2L, Collections.emptyList()),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecord().getStatus());
    }

    @Test
    public void testKits_notEnrolled_thenIneligible() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, List.of(
                        new ParticipantStatus.Sample("a", "SALIVA", 1L, null, null, "tracking", "carrier"))),
                EnrollmentStatusType.REGISTERED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.INELIGIBLE, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenNoTimestamps_thenSent() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, List.of(
                        new ParticipantStatus.Sample("a", "SALIVA", 1L, null, null, "tracking", "carrier"))),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.SENT, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenDelivered_thenDelivered() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, List.of(
                        new ParticipantStatus.Sample("a", "SALIVA", 1L, 2L, null, "tracking", "carrier"))),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.DELIVERED, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenDeliveredAndReceived_thenReceived() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, List.of(
                        new ParticipantStatus.Sample("a", "SALIVA", 1L, 2L, 3L, "tracking", "carrier"))),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.RECEIVED, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenNotDeliveredButReceived_thenReceived() {
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatus(null, null, null, null, null, List.of(
                        new ParticipantStatus.Sample("a", "SALIVA", 1L, null, 2L, "tracking", "carrier"))),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.RECEIVED, info.getKits().get(0).getStatus());
    }
}

package org.broadinstitute.ddp.model.dsm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.Collections;

import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo.RecordStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.junit.Test;

public class ParticipantStatusTrackingInfoTest {

    @Test
    public void testRecords_notEnrolled_thenIneligible() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                LocalDate.of(2019, 5, 11), "NAME", LocalDate.of(2019, 6, 1), "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 2, 23), LocalDate.of(2020, 1, 15), "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.REGISTERED, "guid");
        assertNotNull(info.getMedicalRecord());
        assertNotNull(info.getTissueRecord());
        assertEquals(RecordStatus.INELIGIBLE, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.INELIGIBLE, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNoTimestamps_thenPending() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                null, "NAME", null, "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, null,
                null, null, "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecord());
        assertNotNull(info.getTissueRecord());
        assertEquals(RecordStatus.PENDING, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.PENDING, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequested_thenSent() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                LocalDate.of(2019, 5, 11), "NAME", null, "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, LocalDate.of(2020, 2, 1),
                null, null, "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecord());
        assertNotNull(info.getTissueRecord());
        assertEquals(RecordStatus.SENT, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.SENT, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequestedAndReceived_thenReceived() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                LocalDate.of(2019, 5, 11), "NAME", LocalDate.of(2019, 6, 1), "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, LocalDate.of(2020, 2, 1),
                LocalDate.of(2020, 2, 23), null, "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecord());
        assertNotNull(info.getTissueRecord());
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecord().getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNotRequestedButReceived_thenReceived() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                null, "NAME", LocalDate.of(2019, 6, 1), "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, null,
                LocalDate.of(2020, 2, 23), null, "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecord());
        assertNotNull(info.getTissueRecord());
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecord().getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecord().getStatus());
    }

    @Test
    public void testKits_notEnrolled_thenIneligible() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", LocalDate.of(2019, 5, 11), LocalDate.of(2019, 5, 11), LocalDate.of(2019, 5, 11), "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.REGISTERED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.INELIGIBLE, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenNoTimestamps_thenSent() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", LocalDate.of(2020, 9, 1), null, null, "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.SENT, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenDelivered_thenDelivered() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", LocalDate.of(2020, 9, 1), LocalDate.of(2020, 9, 9), null, "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.DELIVERED, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenDeliveredAndReceived_thenReceived() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", LocalDate.of(2020, 9, 1), LocalDate.of(2020, 9, 9), LocalDate.of(2020, 9, 9), "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.RECEIVED, info.getKits().get(0).getStatus());
    }

    @Test
    public void testKits_enrolled_whenNotDeliveredButReceived_thenReceived() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", LocalDate.of(2020, 9, 1), null, LocalDate.of(2020, 9, 9), "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.RECEIVED, info.getKits().get(0).getStatus());
    }
}

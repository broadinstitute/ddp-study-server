package org.broadinstitute.ddp.model.dsm;

import org.broadinstitute.ddp.model.dsm.ParticipantStatusTrackingInfo.RecordStatus;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ParticipantStatusTrackingInfoTest {

    @Test
    public void testRecords_notEnrolled_thenIneligible() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                "2019-05-11", "PARTICIPANTID", "NAME", "2019-06-01", "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, "2020-01-01",
                "2020-02-23", "2020-01-15", "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.REGISTERED, "guid");
        assertNotNull(info.getMedicalRecords());
        assertNotNull(info.getTissueRecords());
        assertEquals(1, info.getMedicalRecords().size());
        assertEquals(1, info.getTissueRecords().size());
        assertEquals(RecordStatus.INELIGIBLE, info.getMedicalRecords().get(0).getStatus());
        assertEquals(RecordStatus.INELIGIBLE, info.getTissueRecords().get(0).getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNoTimestamps_thenPending() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                "", "PARTICIPANTID", "NAME", "", "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, "",
                "", "", "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecords());
        assertNotNull(info.getTissueRecords());
        assertEquals(1, info.getMedicalRecords().size());
        assertEquals(1, info.getTissueRecords().size());
        assertEquals(RecordStatus.PENDING, info.getMedicalRecords().get(0).getStatus());
        assertEquals(RecordStatus.PENDING, info.getTissueRecords().get(0).getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequested_thenSent() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                "2019-05-11", "PARTICIPANTID", "NAME", "", "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, "2020-01-01",
                "", "", "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecords());
        assertNotNull(info.getTissueRecords());
        assertEquals(1, info.getMedicalRecords().size());
        assertEquals(1, info.getTissueRecords().size());
        assertEquals(RecordStatus.SENT, info.getMedicalRecords().get(0).getStatus());
        assertEquals(RecordStatus.SENT, info.getTissueRecords().get(0).getStatus());
    }

    @Test
    public void testRecords_enrolled_whenRequestedAndReceived_thenReceived() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                "2019-05-11", "PARTICIPANTID", "NAME", "2019-06-01", "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, "2020-01-01",
                "2020-02-23", "", "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecords());
        assertNotNull(info.getTissueRecords());
        assertEquals(1, info.getMedicalRecords().size());
        assertEquals(1, info.getTissueRecords().size());
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecords().get(0).getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecords().get(0).getStatus());
    }

    @Test
    public void testRecords_enrolled_whenNotRequestedButReceived_thenReceived() {
        ParticipantStatusES.MedicalRecord mr = new ParticipantStatusES.MedicalRecord(1L,
                "", "PARTICIPANTID", "NAME", "2019-06-01", "TYPE");
        ParticipantStatusES.TissueRecord tr = new ParticipantStatusES.TissueRecord(1L, "",
                "2020-02-23", "", "TYPEPX", "LOCATIONPX", "DATEPX", "H",
                "A");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(Collections.singletonList(mr), Collections.singletonList(tr), null, null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertNotNull(info.getMedicalRecords());
        assertNotNull(info.getTissueRecords());
        assertEquals(1, info.getMedicalRecords().size());
        assertEquals(1, info.getTissueRecords().size());
        assertEquals(RecordStatus.RECEIVED, info.getMedicalRecords().get(0).getStatus());
        assertEquals(RecordStatus.RECEIVED, info.getTissueRecords().get(0).getStatus());
    }

    @Test
    public void testKits_notEnrolled_thenIneligible() {
        ParticipantStatusES.Sample sample = new ParticipantStatusES.Sample("1",
                "type", "2019-05-11", "2019-05-11", "2019-05-11", "TRACKIN", "TRACKOUT",
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
                "type", "", "", "", "TRACKIN", "TRACKOUT",
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
                "type", "", "2020-09-09", "", "TRACKIN", "TRACKOUT",
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
                "type", "", "2020-09-09", "2020-09-09", "TRACKIN", "TRACKOUT",
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
                "type", "", "", "2020-09-09", "TRACKIN", "TRACKOUT",
                "CARRIER");
        var info = new ParticipantStatusTrackingInfo(
                new ParticipantStatusES(null, null, Collections.singletonList(sample), null),
                EnrollmentStatusType.ENROLLED, "guid");
        assertEquals(1, info.getKits().size());
        assertEquals(RecordStatus.RECEIVED, info.getKits().get(0).getStatus());
    }
}

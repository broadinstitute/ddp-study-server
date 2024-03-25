package org.broadinstitute.dsm.service.adminoperation;

import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.lddp.handlers.util.Institution;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class MedicalRecordInitServiceTest extends DbAndElasticBaseTest {
    private static final String instanceName = "mrinitservice";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static DDPInstance ddpInstance;
    private static int participantCounter = 0;
    private static final ParticipantDao participantDao = new ParticipantDao();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        ddpInstance = DDPInstance.getDDPInstanceById(ddpInstanceDto.getDdpInstanceId());
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(ddpInstanceDto);
        TestParticipantUtil.deleteInstanceParticipants(ddpInstanceDto);
    }

    @Test
    public void testWriteMedicalRecordBundle() {
        String ddpParticipantId = TestParticipantUtil.createMinimalParticipant(ddpInstanceDto, participantCounter++);
        Dsm dsm = new Dsm();
        dsm.setHasConsentedToBloodDraw(true);
        ElasticTestUtil.addParticipantDsm(esIndex, dsm, ddpParticipantId);

        String institutionId = "testInstitutionId";
        Institution institution = new Institution(institutionId, "PHYSICIAN");
        long institutionRequestId = 123;
        String updated = "2024-03-01";
        InstitutionRequest institutionRequest =
                new InstitutionRequest(institutionRequestId, ddpParticipantId, List.of(institution), updated);

        UpdateLog updateLog = MedicalRecordInitService.writeMedicalRecordBundle(institutionRequest, ddpInstance);
        log.debug("Update log: " + updateLog);
        Assert.assertEquals(UpdateLog.UpdateStatus.UPDATED, updateLog.getStatus());

        ParticipantDto participantDto = verifyParticipant(ddpParticipantId, ddpInstanceDto);
        Assert.assertEquals(updated, participantDto.getLastVersionDate().get());
        Assert.assertEquals(institutionRequestId, participantDto.getLastVersion().get().longValue());

        verifyMedicalRecordBundle(participantDto, ddpInstanceDto, 1);

        institutionId = "testInstitutionId2";
        institution = new Institution(institutionId, "INSTITUTION");
        institutionRequestId = 124;
        updated = "2024-03-02";
        institutionRequest =
                new InstitutionRequest(institutionRequestId, ddpParticipantId, List.of(institution), updated);

        updateLog = MedicalRecordInitService.writeMedicalRecordBundle(institutionRequest, ddpInstance);
        log.debug("Update log: " + updateLog);
        Assert.assertEquals(UpdateLog.UpdateStatus.UPDATED, updateLog.getStatus());

        participantDto = verifyParticipant(ddpParticipantId, ddpInstanceDto);
        Assert.assertEquals(updated, participantDto.getLastVersionDate().get());
        Assert.assertEquals(institutionRequestId, participantDto.getLastVersion().get().longValue());

        verifyMedicalRecordBundle(participantDto, ddpInstanceDto, 2);
    }

    /**
     * Verify a medical record bundle for a participant is present in the DB and ES
     *
     * @param count expected count of medical records
     */
    public static void verifyMedicalRecordBundle(ParticipantDto participant, DDPInstanceDto ddpInstanceDto, int count) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        String esIndex = ddpInstanceDto.getEsParticipantIndex();

        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(count, esMedicalRecords.size());

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(count, medicalRecords.size());

        Optional<ParticipantRecordDto> participantRecord =
                ParticipantRecordDao.of().getParticipantRecordByParticipantId(participantId);
        Assert.assertTrue(participantRecord.isPresent());

        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        Assert.assertTrue(oncHistory.isPresent());
        Assert.assertNull(oncHistory.get().getCreated());
    }

    /**
     * Verify that a participant exists in DSM and ES
     */
    public static ParticipantDto verifyParticipant(String ddpParticipantId, DDPInstanceDto ddpInstanceDto) {
        int ddpInstanceId = ddpInstanceDto.getDdpInstanceId();
        Optional<ParticipantDto> ptpRes = participantDao.getParticipantForInstance(ddpParticipantId, ddpInstanceId);
        Assert.assertTrue(ptpRes.isPresent());
        ParticipantDto participantDto = ptpRes.get();
        Assert.assertEquals(ddpParticipantId, participantDto.getRequiredDdpParticipantId());

        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        Optional<Participant> ptp = dsm.getParticipant();
        Assert.assertTrue(ptp.isPresent());
        Participant participant = ptp.get();

        Assert.assertEquals(ddpParticipantId, participant.getDdpParticipantId());
        Assert.assertEquals(ddpInstanceDto.getDdpInstanceId(), participant.getDdpInstanceId());
        return participantDto;
    }
}

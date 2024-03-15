package org.broadinstitute.dsm.service.medicalrecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.lddp.handlers.util.InstitutionRequest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class MedicalRecordServiceTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final String instanceName = "medrecord";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static MedicalRecordTestUtil medicalRecordTestUtil;

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getRequiredParticipantId()));
        participants.clear();
    }

    @Test
    public void testProcessInstitutionRequest() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceName);
        InstitutionRequest institutionRequest = new InstitutionRequest();
        InstitutionRequest[] institutionRequests = new InstitutionRequest[] {institutionRequest};

        MedicalRecordService.processInstitutionRequest(institutionRequests, ddpInstance, 0);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private void verifyMedicalRecordAndOncHistory(ParticipantDto participant) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<OncHistoryDetail> esOncHistoryDetailList = dsm.getOncHistoryDetail();
        Assert.assertEquals(1, esOncHistoryDetailList.size());

        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(1, esMedicalRecords.size());

        Optional<OncHistory> oh = dsm.getOncHistory();
        Assert.assertTrue(oh.isPresent());
        OncHistory esOncHistory = oh.get();

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(1, medicalRecords.size());

        int medicalRecordId = medicalRecords.get(0).getMedicalRecordId();
        Assert.assertEquals(medicalRecordId, esMedicalRecords.get(0).getMedicalRecordId());

        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        Assert.assertTrue(oncHistory.isPresent());
        Assert.assertEquals(oncHistory.get().getCreated(), esOncHistory.getCreated());
        Assert.assertEquals(oncHistory.get().getReviewed(), esOncHistory.getReviewed());
    }
}

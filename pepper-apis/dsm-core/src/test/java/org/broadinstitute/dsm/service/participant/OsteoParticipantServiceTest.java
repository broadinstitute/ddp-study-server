package org.broadinstitute.dsm.service.participant;

import static org.broadinstitute.dsm.service.participant.OsteoParticipantService.OSTEO1_COHORT_TAG_NAME;
import static org.broadinstitute.dsm.service.participant.OsteoParticipantService.OSTEO2_COHORT_TAG_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class OsteoParticipantServiceTest extends DbAndElasticBaseTest {
    private static final String osteo1InstanceName = "osteo1test";
    private static final String osteo2InstanceName = "osteo2test";

    private static String osteo1EsIndex;
    private static String osteo2EsIndex;
    private static DDPInstanceDto osteo1InstanceDto;
    private static DDPInstanceDto osteo2InstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static MedicalRecordTestUtil medicalRecordTestUtil;
    private static CohortTagTestUtil cohortTagTestUtil;
    private static final ParticipantRecordDao participantRecordDao = new ParticipantRecordDao();


    @BeforeClass
    public static void setup() throws Exception {
        osteo1EsIndex = ElasticTestUtil.createIndex(osteo1InstanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        osteo1InstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(osteo1InstanceName, osteo1EsIndex);
        osteo2EsIndex = ElasticTestUtil.createIndex(osteo2InstanceName, "elastic/osteo1Mappings.json",
                "elastic/osteo1Settings.json");
        osteo2InstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(osteo2InstanceName, osteo2EsIndex);
        medicalRecordTestUtil = new MedicalRecordTestUtil();
        cohortTagTestUtil = new CohortTagTestUtil();
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(osteo1InstanceDto);
        ElasticTestUtil.deleteIndex(osteo1EsIndex);
        DdpInstanceGroupTestUtil.deleteInstance(osteo2InstanceDto);
        ElasticTestUtil.deleteIndex(osteo2EsIndex);
    }

    @After
    public void deleteParticipantData() {
        cohortTagTestUtil.tearDown();
        CohortTagTestUtil.deleteInstanceTags(osteo1InstanceName);
        CohortTagTestUtil.deleteInstanceTags(osteo2InstanceName);
        medicalRecordTestUtil.tearDown();
        MedicalRecordTestUtil.deleteInstanceMedicalRecordBundles(osteo2InstanceDto);

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
        TestParticipantUtil.deleteInstanceParticipants(osteo2InstanceDto);
    }

    @Test
    public void testCreateDefaultData() {
        ParticipantDto participant = createParticipant(osteo2InstanceDto);
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        ElasticSearchParticipantDto esParticipantDto =
                ElasticSearchUtil.getParticipantESDataByParticipantId(osteo2EsIndex, ddpParticipantId);

        OsteoParticipantService osteoParticipantService =
                new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
        osteoParticipantService.setOsteo2DefaultData(ddpParticipantId, esParticipantDto);
        verifyCohortTag(ddpParticipantId, OSTEO2_COHORT_TAG_NAME, osteo2InstanceDto);
    }

    @Test
    public void testInitializeReconsentedParticipant() {
        ParticipantDto participant = setupOsteo1Participant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // setup osteo2 ptp
        ElasticTestUtil.addParticipantProfileFromFile(osteo2EsIndex, "elastic/participantProfile.json",
                ddpParticipantId);

        try {
            OsteoParticipantService osteoParticipantService =
                    new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
            osteoParticipantService.initializeReconsentedParticipant(ddpParticipantId);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to initialize reconsented participant: " + e.getMessage());
        }

        verifyOsteo2Participant(participant, 2);
    }

    @Test
    public void testInitializeReconsentedParticipantWithDsm() {
        ParticipantDto participant = setupOsteo1Participant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // setup osteo2 ptp
        ElasticTestUtil.addParticipantProfileFromFile(osteo2EsIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        Dsm dsm = new Dsm();
        dsm.setHasConsentedToBloodDraw(true);
        ElasticTestUtil.addParticipantDsm(osteo2EsIndex, dsm, ddpParticipantId);

        try {
            OsteoParticipantService osteoParticipantService =
                    new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
            osteoParticipantService.initializeReconsentedParticipant(ddpParticipantId);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to initialize reconsented participant: " + e.getMessage());
        }

        verifyOsteo2Participant(participant, 2);
    }

    private ParticipantDto setupOsteo1Participant() {
        ParticipantDto participant = createParticipant(osteo1InstanceDto);
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // create two medical records for the participant
        medicalRecordTestUtil.createMedicalRecordBundle(participant, osteo1InstanceDto);
        medicalRecordTestUtil.createMedicalRecordBundle(participant, osteo1InstanceDto);

        cohortTagTestUtil.createTag(OSTEO1_COHORT_TAG_NAME, ddpParticipantId, osteo1InstanceDto.getDdpInstanceId());
        return participant;
    }

    private void verifyOsteo2Participant(ParticipantDto participant, int medicalRecordsCount) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        verifyParticipant(ddpParticipantId, osteo2InstanceDto);
        verifyMedicalRecords(participant, osteo2InstanceDto, medicalRecordsCount);
        verifyCohortTag(ddpParticipantId, OSTEO2_COHORT_TAG_NAME, osteo2InstanceDto);
    }

    private ParticipantDto createParticipant(DDPInstanceDto ddpInstanceDto) {
        String baseName = String.format("%s_%d", ddpInstanceDto.getInstanceName(), participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private void verifyParticipant(String ddpParticipantId, DDPInstanceDto ddpInstanceDto) {
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
    }

    private void verifyCohortTag(String ddpParticipantId, String tag, DDPInstanceDto ddpInstanceDto) {
        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<CohortTag> tags = dsm.getCohortTag();
        Assert.assertEquals(1, tags.size());
        Assert.assertEquals(tag, tags.get(0).getCohortTagName());
    }

    private void verifyMedicalRecords(ParticipantDto participant, DDPInstanceDto ddpInstanceDto, int count) {
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
                participantRecordDao.getParticipantRecordByParticipantId(participantId);
        Assert.assertTrue(participantRecord.isPresent());
    }
}

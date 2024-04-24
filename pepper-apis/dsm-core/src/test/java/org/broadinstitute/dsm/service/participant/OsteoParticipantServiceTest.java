package org.broadinstitute.dsm.service.participant;

import static org.broadinstitute.dsm.service.participant.OsteoParticipantService.OSTEO1_COHORT_TAG_NAME;
import static org.broadinstitute.dsm.service.participant.OsteoParticipantService.OSTEO2_COHORT_TAG_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistory;
import org.broadinstitute.dsm.db.Participant;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantRecordDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantRecordDto;
import org.broadinstitute.dsm.db.dto.onchistory.OncHistoryDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
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
    private static final ParticipantDao participantDao = new ParticipantDao();
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();


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

        ElasticTestUtil.addActivitiesFromFile(osteo2EsIndex, "elastic/osteo2Activities.json", ddpParticipantId);

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, osteo2EsIndex);

        OsteoParticipantService osteoParticipantService =
                new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
        osteoParticipantService.setOsteoDefaultData(ddpParticipantId, esParticipantDto);
        verifyCohortTags(ddpParticipantId, osteo2InstanceDto, List.of(OSTEO2_COHORT_TAG_NAME));
    }

    @Test
    public void testCreateOsteo1DefaultData() {
        ParticipantDto participant = createParticipant(osteo1InstanceDto);
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        ElasticTestUtil.addActivitiesFromFile(osteo1EsIndex, "elastic/osteo1Activities.json", ddpParticipantId);

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, osteo1EsIndex);

        OsteoParticipantService osteoParticipantService =
                new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
        osteoParticipantService.setOsteoDefaultData(ddpParticipantId, esParticipantDto);
        verifyCohortTags(ddpParticipantId, osteo1InstanceDto, List.of(OSTEO1_COHORT_TAG_NAME));
    }

    @Test
    public void testCreateOsteo1DefaultDataNoConsent() {
        ParticipantDto participant = createParticipant(osteo1InstanceDto);
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        ElasticTestUtil.addActivitiesFromFile(osteo1EsIndex, "elastic/osteoActivitiesNoConsent.json", ddpParticipantId);

        // create a minimal participant profile in osteo2 that matches the profile in osteo1
        ElasticTestUtil.addParticipantProfileFromFile(osteo2EsIndex,
                "elastic/participantProfile.json", ddpParticipantId);

        ElasticSearchParticipantDto esParticipantDto =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, osteo1EsIndex);

        OsteoParticipantService osteoParticipantService =
                new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
        osteoParticipantService.setOsteoDefaultData(ddpParticipantId, esParticipantDto);

        // when no consent, an osteo2 cohort tag should be created
        verifyCohortTags(ddpParticipantId, osteo2InstanceDto, List.of(OSTEO2_COHORT_TAG_NAME));
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

        List<String> tagNames = List.of(OSTEO1_COHORT_TAG_NAME, OSTEO2_COHORT_TAG_NAME);
        verifyOsteo2Participant(participant, 2, tagNames);
        verifyCohortTags(ddpParticipantId, osteo1InstanceDto, tagNames);
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

        List<String> tagNames = List.of(OSTEO1_COHORT_TAG_NAME, OSTEO2_COHORT_TAG_NAME);
        verifyOsteo2Participant(participant, 2, tagNames);
        verifyCohortTags(ddpParticipantId, osteo1InstanceDto, tagNames);
    }

    @Test
    public void testInitializeReconsentedParticipantNoParticipant() {
        // make a ptp with no Participant record in osteo1
        String ddpParticipantId = TestParticipantUtil.createMinimalParticipant(osteo1InstanceDto, participantCounter++);
        // but with a couple of cohort
        Dsm osteo1Dsm = new Dsm();
        createCohortTag(OSTEO1_COHORT_TAG_NAME, ddpParticipantId, osteo1InstanceDto, osteo1Dsm);
        createCohortTag("FOO", ddpParticipantId, osteo1InstanceDto, osteo1Dsm);

        ElasticTestUtil.addParticipantDsm(osteo1EsIndex, osteo1Dsm, ddpParticipantId);

        // setup osteo2 ptp
        ElasticTestUtil.addParticipantProfileFromFile(osteo2EsIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        Dsm osteo2Dsm = new Dsm();
        osteo2Dsm.setHasConsentedToBloodDraw(true);
        ElasticTestUtil.addParticipantDsm(osteo2EsIndex, osteo2Dsm, ddpParticipantId);

        try {
            OsteoParticipantService osteoParticipantService =
                    new OsteoParticipantService(osteo1InstanceName, osteo2InstanceName);
            osteoParticipantService.initializeReconsentedParticipant(ddpParticipantId);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Failed to initialize reconsented participant: " + e.getMessage());
        }

        // when no osteo1 Participant record, initializeReconsentedParticipant only creates an osteo2 cohort tag
        verifyCohortTags(ddpParticipantId, osteo2InstanceDto, List.of(OSTEO1_COHORT_TAG_NAME, "FOO", OSTEO2_COHORT_TAG_NAME));
        List<ParticipantDto> participants = participantDao.getParticipant(ddpParticipantId);
        Assert.assertTrue(participants.isEmpty());
    }

    private ParticipantDto setupOsteo1Participant() {
        ParticipantDto participant = createParticipant(osteo1InstanceDto);
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        // create two medical records for the participant
        medicalRecordTestUtil.createMedicalRecordBundle(participant, osteo1InstanceDto);
        medicalRecordTestUtil.createMedicalRecordBundle(participant, osteo1InstanceDto);

        createCohortTag(OSTEO1_COHORT_TAG_NAME, ddpParticipantId, osteo1InstanceDto,
                getParticipantEsDsm(ddpParticipantId, osteo1EsIndex));
        return participant;
    }

    private void verifyOsteo2Participant(ParticipantDto participant, int medicalRecordsCount, List<String> cohortTags) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        verifyParticipant(ddpParticipantId, osteo2InstanceDto);
        verifyMedicalRecords(participant, osteo2InstanceDto, medicalRecordsCount);
        verifyCohortTags(ddpParticipantId, osteo2InstanceDto, cohortTags);
    }

    private ParticipantDto createParticipant(DDPInstanceDto ddpInstanceDto) {
        String baseName = String.format("%s_%d", ddpInstanceDto.getInstanceName(), participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto);
        participants.add(participant);
        return participant;
    }

    private void createCohortTag(String cohortTagName, String ddpParticipantId, DDPInstanceDto ddpInstanceDto, Dsm dsm) {
        CohortTag cohortTag = cohortTagTestUtil.createTag(cohortTagName, ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        OsteoParticipantService.updateEsCohortTags(dsm, ddpParticipantId, cohortTag, ddpInstanceDto.getEsParticipantIndex());
    }

    private void verifyParticipant(String ddpParticipantId, DDPInstanceDto ddpInstanceDto) {
        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = getParticipantEsDsm(ddpParticipantId, esIndex);

        Optional<Participant> ptp = dsm.getParticipant();
        Assert.assertTrue(ptp.isPresent());
        Participant participant = ptp.get();

        Assert.assertEquals(ddpParticipantId, participant.getDdpParticipantId());
        Assert.assertEquals(ddpInstanceDto.getDdpInstanceId(), participant.getDdpInstanceId());
    }

    private void verifyCohortTags(String ddpParticipantId, DDPInstanceDto ddpInstanceDto, List<String> tags) {
        String esIndex = ddpInstanceDto.getEsParticipantIndex();
        log.debug("Verifying ES cohort tags for {}, instance {}: {}", ddpParticipantId, ddpInstanceDto.getInstanceName(),
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = getParticipantEsDsm(ddpParticipantId, esIndex);

        List<CohortTag> esTags = dsm.getCohortTag();
        Assert.assertEquals(tags.size(), esTags.size());
        tags.forEach(tag -> {
            Optional<CohortTag> esTag = esTags.stream()
                    .filter(t -> tag.equals(t.getCohortTagName()))
                    .findAny();
            Assert.assertTrue(esTag.isPresent());
        });
    }

    private void verifyMedicalRecords(ParticipantDto participant, DDPInstanceDto ddpInstanceDto, int count) {
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        String esIndex = ddpInstanceDto.getEsParticipantIndex();

        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = getParticipantEsDsm(ddpParticipantId, esIndex);

        List<MedicalRecord> esMedicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(count, esMedicalRecords.size());

        int participantId = participant.getRequiredParticipantId();
        List<MedicalRecord> medicalRecords = MedicalRecord.getMedicalRecordsForParticipant(participantId);
        Assert.assertEquals(count, medicalRecords.size());

        Optional<ParticipantRecordDto> participantRecord =
                participantRecordDao.getParticipantRecordByParticipantId(participantId);
        Assert.assertTrue(participantRecord.isPresent());

        Optional<OncHistory> oh = dsm.getOncHistory();
        Assert.assertTrue(oh.isPresent());
        OncHistory esOncHistory = oh.get();

        Optional<OncHistoryDto> oncHistory = OncHistoryDao.getByParticipantId(participantId);
        Assert.assertTrue(oncHistory.isPresent());
        Assert.assertEquals(oncHistory.get().getCreated(), esOncHistory.getCreated());
        Assert.assertEquals(oncHistory.get().getReviewed(), esOncHistory.getReviewed());
    }

    private Dsm getParticipantEsDsm(String ddpParticipantId, String esIndex) {
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        return esParticipant.getDsm().orElseThrow();
    }
}

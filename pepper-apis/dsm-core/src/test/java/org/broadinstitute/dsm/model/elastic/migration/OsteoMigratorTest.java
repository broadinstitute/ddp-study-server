package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.onchistory.OncHistoryDetailDaoImpl;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.KitShippingTestUtil;
import org.broadinstitute.dsm.util.MedicalRecordTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

@Slf4j
public class OsteoMigratorTest extends DbAndElasticBaseTest {
    private static final String TEST_USER = "TEST_USER";
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final OncHistoryDetailDaoImpl oncHistoryDetailDao = new OncHistoryDetailDaoImpl();
    private static DDPInstanceDto os1DdpInstanceDto;
    private static DDPInstanceDto os2DdpInstanceDto;
    private static String os1InstanceName;
    private static String os2InstanceName;
    private static String esIndex;
    private static int participantCounter;
    private static KitShippingTestUtil kitShippingTestUtil;
    private static final List<ParticipantDto> os1Participants = new ArrayList<>();
    private static final List<ParticipantDto> os2Participants = new ArrayList<>();
    private static final Map<Integer, Integer> participantToMedicalRecordId = new HashMap<>();
    private static final Map<Integer, Integer> participantToOncHistoryId = new HashMap<>();
    private static final String OS1_TAG = "OS";
    private static final String OS2_TAG = "OS PE-CGS";
    private static final String baseInstanceName = "osteomigrator";

    private enum Cohort {
        OS1, OS2, OS1_OS2
    }

    @BeforeClass
    public static void setup() throws Exception {
        participantCounter = 1;
        // NEW_OSTEO_INSTANCE_NAME and OLD_OSTEO_INSTANCE_NAME are already hardcoded in the cohort export code,
        // so we either need to use them or change the code under test. Since the latter really needs a rewrite
        // (with tests!) we do it this way for now.
        // TODO: revisit this -- DC
        os1InstanceName = OLD_OSTEO_INSTANCE_NAME;
        os2InstanceName = NEW_OSTEO_INSTANCE_NAME;
        esIndex = ElasticTestUtil.createIndex(baseInstanceName, "elastic/lmsMappings.json", null);
        // OS1 instance is not in the test DB, OS2 is
        os1DdpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(os1InstanceName, esIndex);
        os2DdpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(os2InstanceName).orElseThrow();
        ddpInstanceDao.updateEsParticipantIndex(os2DdpInstanceDto.getDdpInstanceId(), esIndex);
        kitShippingTestUtil = new KitShippingTestUtil(TEST_USER, baseInstanceName);
    }

    @AfterClass
    public static void tearDown() {
        ElasticTestUtil.deleteIndex(esIndex);
        ddpInstanceDao.delete(os1DdpInstanceDto.getDdpInstanceId());
    }

    @After
    public void deleteParticipantData() {
        kitShippingTestUtil.tearDown();
        participantToOncHistoryId.values().forEach(oncHistoryDetailDao::delete);
        participantToOncHistoryId.clear();
        participantToMedicalRecordId.values().forEach(MedicalRecordTestUtil::deleteMedicalRecord);
        participantToMedicalRecordId.clear();
        os1Participants.forEach(ptp -> {
            CohortTagTestUtil.deleteTag(ptp.getDdpParticipantIdOrThrow(), OS1_TAG);
            TestParticipantUtil.deleteParticipant(ptp.getParticipantId().orElseThrow());
        });
        os1Participants.clear();
        os2Participants.forEach(ptp -> {
            CohortTagTestUtil.deleteTag(ptp.getDdpParticipantIdOrThrow(), OS2_TAG);
            TestParticipantUtil.deleteParticipant(ptp.getParticipantId().orElseThrow());
        });
        os2Participants.clear();
    }

    @Test
    public void cohortTagExportTest() {
        // create participants and tags for OS1, OS2 and for both
        createParticipantAndCohortTag(Cohort.OS1);
        createParticipantAndCohortTag(Cohort.OS2);
        createParticipantAndCohortTag(Cohort.OS1_OS2);

        // do an OS1 export
        CohortTagMigrator os1Migrator = new CohortTagMigrator(esIndex, os1InstanceName, new CohortTagDaoImpl());
        try {
            os1Migrator.export();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception exporting to ES" + e);
        }
        verifyCohortTags();

        // do an OS2 export
        CohortTagMigrator os2Migrator = new CohortTagMigrator(esIndex, os2InstanceName, new CohortTagDaoImpl());
        try {
            os2Migrator.export();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception exporting to ES" + e);
        }
        verifyCohortTags();

        // do an OS1 export again (to test OS2 -> OS1 sequence)
        try {
            os1Migrator.export();
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception exporting to ES" + e);
        }
        verifyCohortTags();
    }

    @Test
    public void testOsteoMigrator() {
        // create OS1 only participants
        List<ParticipantDto> ptp1 = createParticipantAndCohortTag(Cohort.OS1);
        ParticipantDto os1Ptp = ptp1.get(0);
        createOncHistoryDetail(os1Ptp, os1DdpInstanceDto, 1);
        kitShippingTestUtil.createTestKitShipping(os1Ptp, os1DdpInstanceDto);

        // create OS2 only participants
        List<ParticipantDto> ptp2 = createParticipantAndCohortTag(Cohort.OS2);
        ParticipantDto os2Ptp = ptp2.get(0);
        createOncHistoryDetail(os2Ptp, os2DdpInstanceDto, 1);
        kitShippingTestUtil.createTestKitShipping(os2Ptp, os1DdpInstanceDto);

        // create OS1 participant consented to OS2
        List<ParticipantDto> ptp3 = createParticipantAndCohortTag(Cohort.OS1_OS2);
        ParticipantDto os1BothPtp = ptp3.get(0);
        ParticipantDto os2BothPtp = ptp3.get(1);
        createOncHistoryDetail(os1BothPtp, os1DdpInstanceDto, 1);
        createOncHistoryDetail(os2BothPtp, os2DdpInstanceDto, 1);
        kitShippingTestUtil.createTestKitShipping(os1BothPtp, os1DdpInstanceDto);
        kitShippingTestUtil.createTestKitShipping(os2BothPtp, os1DdpInstanceDto);

        // do an OS1 export
        try {
            StudyMigrator.migrate(os1InstanceName);
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception exporting to ES" + e);
        }

        // should be onc history for OS1 only participant
        verifyOncHistory(os1Ptp.getDdpParticipantIdOrThrow(), List.of(os1Ptp.getParticipantIdOrThrow()));
        // should be no onc history for OS2 only participant (not exported)
        //!!TODO
        // for OS1 participant consented to OS2 there are  two onc history records, one for each instance
        verifyOncHistory(os1BothPtp.getDdpParticipantIdOrThrow(),
                List.of(os1BothPtp.getParticipantIdOrThrow(), os2BothPtp.getParticipantIdOrThrow()));

        // do an OS2 export
        try {
            StudyMigrator.migrate(os2InstanceName);
            TimeUnit.SECONDS.sleep(1);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception exporting to ES" + e);
        }
        // should be onc history for OS2 only participant
        verifyOncHistory(os2Ptp.getDdpParticipantIdOrThrow(), List.of(os2Ptp.getParticipantIdOrThrow()));
        // should be onc history for OS1 only participant
        verifyOncHistory(os1Ptp.getDdpParticipantIdOrThrow(), List.of(os1Ptp.getParticipantIdOrThrow()));
        // for OS1 participant consented to OS2 there should be two onc history records, one for each instance
        verifyOncHistory(os1BothPtp.getDdpParticipantIdOrThrow(),
                List.of(os1BothPtp.getParticipantIdOrThrow(), os2BothPtp.getParticipantIdOrThrow()));

        // expected data for OS1 only
        // expected data for OS2 only
        // expected data for OS1 consented to OS2
    }

    private void verifyCohortTags() {
        try {
            os1Participants.forEach(ptp -> {
                String ddpParticipantId = ptp.getDdpParticipantIdOrThrow();
                //!! change to log.debug
                log.info("ES participant record for {}: {}",  ddpParticipantId,
                        ElasticTestUtil.getParticipantDocumentAsString(esIndex,  ddpParticipantId));
                List<CohortTag> cohortTags = getCohortTagsFromDoc(ddpParticipantId);
                log.info("TEMP: participant {} has cohort tags {}", ddpParticipantId, cohortTags);
                Assert.assertTrue(cohortTags.stream().anyMatch(tag -> tag.getCohortTagName().equals(OS1_TAG)));
            });
            os2Participants.forEach(ptp -> {
                String ddpParticipantId = ptp.getDdpParticipantIdOrThrow();
                log.debug("ES participant record for {}: {}",  ddpParticipantId,
                        ElasticTestUtil.getParticipantDocumentAsString(esIndex,  ddpParticipantId));
                List<CohortTag> cohortTags = getCohortTagsFromDoc(ddpParticipantId);
                log.info("TEMP: participant {} has cohort tags {}", ddpParticipantId, cohortTags);
                Assert.assertTrue(cohortTags.stream().anyMatch(tag -> tag.getCohortTagName().equals(OS2_TAG)));
            });
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Unexpected exception getting ES data" + e);
        }
    }

    private List<ParticipantDto> createParticipantAndCohortTag(Cohort cohort) {
        String baseName = String.format("%s_%s_%d", baseInstanceName, cohort.name(), participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        List<ParticipantDto> participants = new ArrayList<>();
        ParticipantDto participant = null;
        if (cohort == Cohort.OS1 || cohort == Cohort.OS1_OS2) {
            participant = TestParticipantUtil.createParticipant(ddpParticipantId, os1DdpInstanceDto.getDdpInstanceId());
            os1Participants.add(participant);
            participants.add(participant);
            CohortTagTestUtil.createTag(OS1_TAG, ddpParticipantId, os1DdpInstanceDto.getDdpInstanceId());
        }
        if (cohort == Cohort.OS2 || cohort == Cohort.OS1_OS2) {
            participant = TestParticipantUtil.createParticipant(ddpParticipantId, os2DdpInstanceDto.getDdpInstanceId());
            os2Participants.add(participant);
            participants.add(participant);
            CohortTagTestUtil.createTag(OS2_TAG, ddpParticipantId, os2DdpInstanceDto.getDdpInstanceId());
        }
        // we use the OS2 ptp if the cohort is OS1_AND_OS2
        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participants;
    }

    private void verifyOncHistory(String ddpParticipantId, List<Integer> participantIds) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        //TEMP: change to log.debug
        log.info("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        log.info("TEMP: verifying {} participant ids", participantIds.size());
        log.info("TEMP: ES participant record for {} has {} medical records and {} onc history records",
                ddpParticipantId, esParticipant.getDsm().orElseThrow().getMedicalRecord().size(),
                esParticipant.getDsm().orElseThrow().getOncHistoryDetail().size());
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<OncHistoryDetail> oncHistoryDetailList = dsm.getOncHistoryDetail();
        Assert.assertEquals(participantIds.size(), oncHistoryDetailList.size());

        List<MedicalRecord> medicalRecords = dsm.getMedicalRecord();
        Assert.assertEquals(participantIds.size(), medicalRecords.size());

        participantIds.forEach(participantId -> {
            int medicalRecordId = participantToMedicalRecordId.get(participantId);
            OncHistoryDetail oncHistoryDetail = oncHistoryDetailList.stream()
                    .filter(rec -> rec.getMedicalRecordId() == medicalRecordId)
                    .findFirst().orElseThrow();
            log.info("TEMP: oncHistoryDetail: {}", oncHistoryDetail);
            Assert.assertEquals(oncHistoryDetail.getOncHistoryDetailId(),
                    participantToOncHistoryId.get(participantId).intValue());
            Assert.assertTrue(medicalRecords.stream()
                    .anyMatch(rec -> rec.getMedicalRecordId() == medicalRecordId));
        });
    }

    private void createOncHistoryDetail(ParticipantDto participant, DDPInstanceDto instanceDto, int oncHistoryCount) {
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        int participantId = participant.getParticipantIdOrThrow();
        int medicalRecordId = MedicalRecordTestUtil.createMedicalRecord(participant, instanceDto);
        log.info("TEMP: created medical record {} for participant {}", medicalRecordId, participantId);
        participantToMedicalRecordId.put(participantId, medicalRecordId);

        // add some onc history detail records
        for (int i = 1; i <= oncHistoryCount; i++) {
            OncHistoryDetail.Builder builder = new OncHistoryDetail.Builder()
                    .withDdpInstanceId(instanceDto.getDdpInstanceId())
                    .withMedicalRecordId(medicalRecordId)
                    .withFacility(String.format("Office %d", i))
                    .withDestructionPolicy("12")
                    .withChangedBy(TEST_USER);

            OncHistoryDetail rec = builder.build();
            int oncHistoryDetailId = OncHistoryDetail.createOncHistoryDetail(rec);
            participantToOncHistoryId.put(participantId, oncHistoryDetailId);
            rec.setOncHistoryDetailId(oncHistoryDetailId);
            ElasticTestUtil.createOncHistoryDetail(esIndex, rec, ddpParticipantId);
        }

        //TEMP: change to log.debug
        log.info("Created ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
    }

    private List<CohortTag> getCohortTagsFromDoc(String ddpParticipantId) {
        Map<String, Object> sourceMap = ElasticTestUtil.getParticipantDocument(esIndex,  ddpParticipantId);
        Assert.assertNotNull(sourceMap);
        Map<String, Object> dsmProp = (Map<String, Object>) sourceMap.get(ESObjectConstants.DSM);
        Assert.assertNotNull(dsmProp);
        List<Map<String, Object>> cohortTags = (List<Map<String, Object>>) dsmProp.get(ESObjectConstants.COHORT_TAG);
        Assert.assertNotNull(cohortTags);
        return cohortTags.stream().map(tag ->
                ObjectMapperSingleton.instance().convertValue(tag, CohortTag.class)).collect(Collectors.toList());
    }
}

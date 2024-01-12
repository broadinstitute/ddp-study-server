package org.broadinstitute.dsm.model.elastic.migration;

import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.NEW_OSTEO_INSTANCE_NAME;
import static org.broadinstitute.dsm.model.filter.postfilter.StudyPostFilter.OLD_OSTEO_INSTANCE_NAME;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.tag.cohort.CohortTagDaoImpl;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.tag.cohort.CohortTag;
import org.broadinstitute.dsm.statics.ESObjectConstants;
import org.broadinstitute.dsm.util.CohortTagTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.broadinstitute.dsm.util.proxy.jackson.ObjectMapperSingleton;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class CohortTagMigratorTest extends DbAndElasticBaseTest {
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static DDPInstanceDto os1DdpInstanceDto;
    private static DDPInstanceDto os2DdpInstanceDto;
    private static String os1InstanceName;
    private static String os2InstanceName;
    private static String esIndex;
    private static int participantCounter;
    private static final List<ParticipantDto> os1Participants = new ArrayList<>();
    private static final List<ParticipantDto> os2Participants = new ArrayList<>();
    private static final String OS1_TAG = "OS";
    private static final String OS2_TAG = "OS PE-CGS";
    private static final String baseInstanceName = "cohorttag";

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
        // OS1 realm is not in the test DB, OS2 is
        os1DdpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(os1InstanceName, esIndex);
        os2DdpInstanceDto = ddpInstanceDao.getDDPInstanceByInstanceName(os2InstanceName).orElseThrow();
    }

    @AfterClass
    public static void tearDown() {
        ElasticTestUtil.deleteIndex(esIndex);
        ddpInstanceDao.delete(os1DdpInstanceDto.getDdpInstanceId());
    }

    @After
    public void deleteParticipantData() {
        os1Participants.forEach(ptp -> {
            CohortTagTestUtil.deleteTag(ptp.getDdpParticipantIdOrThrow(), OS1_TAG);
            TestParticipantUtil.deleteParticipant(ptp.getParticipantId().orElseThrow());
        });
        os2Participants.forEach(ptp -> {
            CohortTagTestUtil.deleteTag(ptp.getDdpParticipantIdOrThrow(), OS2_TAG);
            TestParticipantUtil.deleteParticipant(ptp.getParticipantId().orElseThrow());
        });
    }

    @Test
    public void xxxTest() {
        // create participants and tags for OS1, OS2 and for both
        createParticipant(Cohort.OS1);
        createParticipant(Cohort.OS2);
        createParticipant(Cohort.OS1_OS2);

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

    private void verifyCohortTags() {
        try {
            os1Participants.forEach(ptp -> {
                String ddpParticipantId = ptp.getDdpParticipantIdOrThrow();
                log.debug("ES participant record for {}: {}",  ddpParticipantId,
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

    private void createParticipant(Cohort cohort) {
        String baseName = String.format("%s_%s_%d", baseInstanceName, cohort.name(), participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ParticipantDto participant = null;
        if (cohort == Cohort.OS1 || cohort == Cohort.OS1_OS2) {
            participant = TestParticipantUtil.createParticipant(ddpParticipantId, os1DdpInstanceDto.getDdpInstanceId());
            os1Participants.add(participant);
            CohortTagTestUtil.createTag(OS1_TAG, ddpParticipantId, os1DdpInstanceDto.getDdpInstanceId());
        }
        if (cohort == Cohort.OS2 || cohort == Cohort.OS1_OS2) {
            participant = TestParticipantUtil.createParticipant(ddpParticipantId, os2DdpInstanceDto.getDdpInstanceId());
            os2Participants.add(participant);
            CohortTagTestUtil.createTag(OS2_TAG, ddpParticipantId, os2DdpInstanceDto.getDdpInstanceId());
        }
        // we use the OS2 ptp if the cohort is OS1_AND_OS2
        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
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

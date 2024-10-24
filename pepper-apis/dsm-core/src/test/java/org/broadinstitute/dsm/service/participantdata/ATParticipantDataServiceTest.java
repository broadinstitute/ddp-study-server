package org.broadinstitute.dsm.service.participantdata;

import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_GROUP_GENOME_STUDY;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_PARTICIPANT_EXIT;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.EXIT_STATUS;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.GENOME_STUDY_CPT_ID;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.GENOMIC_ID_PREFIX;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.exception.ESMissingParticipantDataException;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.service.elastic.ElasticSearchService;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.ParticipantDataTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ATParticipantDataServiceTest extends DbAndElasticBaseTest {
    private static final ElasticSearchService elasticSearchService = new ElasticSearchService();
    private static final String instanceName = "atdefault";
    private static String esIndex;
    private static DDPInstanceDto ddpInstanceDto;
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();

    @BeforeClass
    public static void setup() throws Exception {
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");
        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
    }

    @AfterClass
    public static void tearDown() {
        DdpInstanceGroupTestUtil.deleteInstance(ddpInstanceDto);
        ElasticTestUtil.deleteIndex(esIndex);
    }

    @After
    public void deleteParticipantData() {
        ParticipantDataDao participantDataDao = new ParticipantDataDao();
        List<ParticipantData> participantDataList =
                participantDataDao.getParticipantDataByInstanceId(ddpInstanceDto.getDdpInstanceId());
        participantDataList.forEach(participantData ->
                participantDataDao.delete(participantData.getParticipantDataId()));

        participants.forEach(participantDto ->
                TestParticipantUtil.deleteParticipant(participantDto.getParticipantId().orElseThrow()));
        participants.clear();
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
    }

    @Test
    public void isSelfOrDependentParticipant() {
        Activities esActivities = new Activities();
        esActivities.setActivityCode(ATParticipantDataService.ACTIVITY_CODE_REGISTRATION);
        esActivities.setStatus(ATParticipantDataService.ACTIVITY_COMPLETE);

        Activities esActivities2 = new Activities();
        esActivities2.setActivityCode(ATParticipantDataService.ACTIVITY_CODE_REGISTRATION);
        esActivities2.setStatus(ATParticipantDataService.ACTIVITY_COMPLETE);

        ElasticSearchParticipantDto participantDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(esActivities))
                .build();
        Assert.assertTrue(ATParticipantDataService.isParticipantRegistrationComplete(participantDto));

        participantDto.setActivities(List.of(esActivities2));
        Assert.assertTrue(ATParticipantDataService.isParticipantRegistrationComplete(participantDto));
    }

    @Test
    public void testSetDefaultValuesExceptionMessageWhenParticipantIdNotFound() {
        String nonexistentParticipantId = "NOT_REAL_ID";
        try {
            ATParticipantDataService.generateDefaultData(instanceName, nonexistentParticipantId);
        } catch (ESMissingParticipantDataException e) {
            Assert.assertTrue(String.format("Error message should include the queried participant id %s.  The "
                            + "message given is %s", nonexistentParticipantId, e.getMessage()),
                    e.getMessage().contains("Participant document %s not found".formatted(nonexistentParticipantId)));
        }
    }

    @Test
    public void testSetDefaultValues() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();
        try {
            ATParticipantDataService.generateDefaultData(instanceName, ddpParticipantId);
            Assert.fail("Should throw an exception when no ptp activities in ES");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ESMissingParticipantDataException);
        }

        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        int fieldSettingsId = FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

        try {
            boolean updated = ATParticipantDataService.generateDefaultData(instanceName, ddpParticipantId);
            Assert.assertTrue(updated);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from generateDefaults: " + e.getMessage());
        }

        verifyDefaultParticipantData(ddpParticipantId);
        verifyDefaultElasticData(ddpParticipantId);

        try {
            // should not create additional genomic ids or exit statues
            boolean updated = ATParticipantDataService.generateDefaultData(instanceName, ddpParticipantId);
            Assert.assertFalse(updated);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from generateDefaultData: " + e.getMessage());
        }

        verifyDefaultParticipantData(ddpParticipantId);
    }

    @Test
    public void testConcurrentGenerateDefaults() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getRequiredDdpParticipantId();

        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        int fieldSettingsId = FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

        // call generate defaults on two separate threads
        Thread t1 = new Thread(new RunGenerateDefaults(ddpParticipantId, instanceName));
        t1.start();
        Thread t2 = new Thread(new RunGenerateDefaults(ddpParticipantId, instanceName));
        t2.start();

        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            Assert.fail("InterruptedException from generateDefaultData: " + e.getMessage());
        }

        verifyDefaultParticipantData(ddpParticipantId);
        verifyDefaultElasticData(ddpParticipantId);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        ParticipantDto participant =
                TestParticipantUtil.createParticipantWithEsProfile(baseName, ddpInstanceDto, esIndex);
        participants.add(participant);
        return participant;
    }

    private void verifyDefaultParticipantData(String ddpParticipantId) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(2, ptpDataList.size()); // 1 for exit status, 1 for genomic id
        ptpDataList.forEach(ptpData -> {
            String fieldType = ptpData.getRequiredFieldTypeId();
            Map<String, String> dataMap = ptpData.getDataMap();
            if (fieldType.equals(AT_PARTICIPANT_EXIT)) {
                Assert.assertEquals("0", dataMap.get(EXIT_STATUS));
            } else if (fieldType.equals(AT_GROUP_GENOME_STUDY)) {
                Assert.assertTrue(dataMap.get(GENOME_STUDY_CPT_ID).startsWith(GENOMIC_ID_PREFIX));
            } else {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }

    private void verifyDefaultElasticData(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                elasticSearchService.getRequiredParticipantDocument(ddpParticipantId, esIndex);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<ParticipantData> participantDataList = dsm.getParticipantData();
        Assert.assertEquals(2, participantDataList.size());

        participantDataList.forEach(participantData -> {
            Assert.assertEquals(ddpParticipantId, participantData.getRequiredDdpParticipantId());
            String fieldType = participantData.getRequiredFieldTypeId();
            Map<String, String> dataMap = participantData.getDataMap();

            if (fieldType.equals(AT_PARTICIPANT_EXIT)) {
                Assert.assertEquals("0", dataMap.get(EXIT_STATUS));
            } else if (fieldType.equals(AT_GROUP_GENOME_STUDY)) {
                Assert.assertTrue(dataMap.get(GENOME_STUDY_CPT_ID).startsWith(GENOMIC_ID_PREFIX));
            } else {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }

    private static class RunGenerateDefaults implements Runnable {
        private final String ddpParticipantId;
        private final String instanceName;

        public RunGenerateDefaults(String ddpParticipantId, String instanceName) {
            this.ddpParticipantId = ddpParticipantId;
            this.instanceName = instanceName;
        }

        public void run() {
            try {
                ATParticipantDataService.generateDefaultData(instanceName, ddpParticipantId);
            } catch (Exception e) {
                e.printStackTrace();
                log.error("Error calling generateDefaults for {}", ddpParticipantId, e);
            }
        }
    }
}

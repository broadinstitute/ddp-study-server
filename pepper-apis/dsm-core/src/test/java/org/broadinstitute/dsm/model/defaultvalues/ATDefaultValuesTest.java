package org.broadinstitute.dsm.model.defaultvalues;

import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.AT_PARTICIPANT_EXIT;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.EXIT_STATUS;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.GENOME_STUDY_CPT_ID;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.GENOME_STUDY_FIELD_TYPE;
import static org.broadinstitute.dsm.model.defaultvalues.ATDefaultValues.GENOMIC_ID_PREFIX;

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
import org.broadinstitute.dsm.service.adminoperation.ReferralSourceServiceTest;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.broadinstitute.dsm.util.ElasticTestUtil;
import org.broadinstitute.dsm.util.FieldSettingsTestUtil;
import org.broadinstitute.dsm.util.TestParticipantUtil;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class ATDefaultValuesTest extends DbAndElasticBaseTest {
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
        ATDefaultValues defaultValues = new ATDefaultValues();

        Activities esActivities = new Activities();
        esActivities.setActivityCode(ATDefaultValues.ACTIVITY_CODE_REGISTRATION);
        esActivities.setStatus(ATDefaultValues.COMPLETE);

        Activities esActivities2 = new Activities();
        esActivities2.setActivityCode(ATDefaultValues.ACTIVITY_CODE_REGISTRATION);
        esActivities2.setStatus(ATDefaultValues.COMPLETE);

        ElasticSearchParticipantDto participantDto = new ElasticSearchParticipantDto.Builder()
                .withActivities(List.of(esActivities))
                .build();

        defaultValues.elasticSearchParticipantDto = participantDto;

        Assert.assertTrue(defaultValues.isParticipantRegistrationComplete());

        participantDto.setActivities(List.of(esActivities2));

        Assert.assertTrue(defaultValues.isParticipantRegistrationComplete());
    }

    @Test
    public void testSetDefaultValuesExceptionMessageWhenParticipantIdNotFound() {
        String nonexistentParticipantId = "NOT_REAL_ID";
        ATDefaultValues atDefaultValues = new ATDefaultValues();
        try {
            atDefaultValues.generateDefaults(instanceName, nonexistentParticipantId);
        } catch (ESMissingParticipantDataException e) {
            Assert.assertTrue(String.format("Error message should include the queried participant id %s.  The "
                    + "message given is %s", nonexistentParticipantId, e.getMessage()),
                    e.getMessage().toUpperCase().contains("PARTICIPANT " + nonexistentParticipantId));
        }
    }

    @Test
    public void testSetDefaultValues() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        ATDefaultValues atDefaultValues = new ATDefaultValues();
        try {
            atDefaultValues.generateDefaults(instanceName, ddpParticipantId);
            Assert.fail("Should throw an exception when no ptp activities in ES");
        } catch (Exception e) {
            Assert.assertTrue(e instanceof ESMissingParticipantDataException);
        }

        List<Activities> activities = ReferralSourceServiceTest.getActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        int fieldSettingsId = FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId());
        fieldSettingsIds.add(fieldSettingsId);

        try {
            boolean updated = atDefaultValues.generateDefaults(instanceName, ddpParticipantId);
            Assert.assertTrue(updated);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from generateDefaults: " + e.getMessage());
        }

        verifyDefaultParticipantData(ddpParticipantId);
        verifyDefaultElasticData(ddpParticipantId);

        try {
            // should not create additional genomic ids or exit statues
            boolean updated = atDefaultValues.generateDefaults(instanceName, ddpParticipantId);
            Assert.assertFalse(updated);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from generateDefaults: " + e.getMessage());
        }

        verifyDefaultParticipantData(ddpParticipantId);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", instanceName, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);

        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        return participant;
    }

    private void verifyDefaultParticipantData(String ddpParticipantId) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantDataByParticipantId(ddpParticipantId);
        Assert.assertEquals(2, ptpDataList.size()); // 1 for exit status, 1 for genomic id
        ptpDataList.forEach(ptpData -> {
            String fieldType = ptpData.getRequiredFieldTypeId();
            Map<String, String> dataMap = ptpData.getDataMap();
            if (fieldType.equals(AT_PARTICIPANT_EXIT)) {
                Assert.assertEquals("0", dataMap.get(EXIT_STATUS));
            } else if (fieldType.equals(GENOME_STUDY_FIELD_TYPE)) {
                Assert.assertTrue(dataMap.get(GENOME_STUDY_CPT_ID).startsWith(GENOMIC_ID_PREFIX));
            } else {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }

    private void verifyDefaultElasticData(String ddpParticipantId) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
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
            } else if (fieldType.equals(GENOME_STUDY_FIELD_TYPE)) {
                Assert.assertTrue(dataMap.get(GENOME_STUDY_CPT_ID).startsWith(GENOMIC_ID_PREFIX));
            } else {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }
}

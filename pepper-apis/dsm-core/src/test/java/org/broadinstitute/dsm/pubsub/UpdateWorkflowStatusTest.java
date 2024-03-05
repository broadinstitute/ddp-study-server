package org.broadinstitute.dsm.pubsub;

import static org.broadinstitute.dsm.model.participant.data.FamilyMemberConstants.MEMBER_TYPE;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_GROUP_GENOME_STUDY;
import static org.broadinstitute.dsm.service.participantdata.ATParticipantDataService.AT_PARTICIPANT_EXIT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbAndElasticBaseTest;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.model.elastic.Activities;
import org.broadinstitute.dsm.model.elastic.Dsm;
import org.broadinstitute.dsm.model.elastic.search.ElasticSearchParticipantDto;
import org.broadinstitute.dsm.route.EditParticipantPublisherRoute;
import org.broadinstitute.dsm.service.participantdata.ATParticipantDataService;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.DdpInstanceGroupTestUtil;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
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
public class UpdateWorkflowStatusTest extends DbAndElasticBaseTest {
    public static final String basename = "workflowstatus";
    public static final String memberTypeFieldTypeId = basename;
    private static final UserDao userDao = new UserDao();
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static Gson gson;
    private static UserDto userDto;
    private static DDPInstanceDto ddpInstanceDto;
    private static String instanceName;
    private static String esIndex;
    private static final List<Integer> fieldSettingsIds = new ArrayList<>();
    private static final List<ParticipantDto> participants = new ArrayList<>();
    private static int participantCounter = 0;

    @BeforeClass
    public static void setup() {
        gson = new Gson();

        instanceName = String.format("%s_%d", basename, Instant.now().toEpochMilli());
        esIndex = ElasticTestUtil.createIndex(instanceName, "elastic/atcpMappings.json",
                "elastic/atcpSettings.json");

        ddpInstanceDto = DdpInstanceGroupTestUtil.createTestDdpInstance(instanceName, esIndex);
        userDto = DBTestUtil.createTestDsmUser("UpdateWorkflowUser", "UpdateWorkflow@status.com", userDao, userDto);

        createMemberTypeFieldSetting();
    }

    @AfterClass
    public static void tearDown() {
        fieldSettingsIds.forEach(FieldSettingsTestUtil::deleteFieldSettings);
        fieldSettingsIds.clear();
        userDao.delete(userDto.getId());
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
    }

    private static ParticipantData createParticipantData(String participantIdSeed) {
        Map<String, String> participantDataMap = new HashMap<>();
        participantDataMap.put("REGISTRATION_STATUS", "REGISTERED");
        participantDataMap.put("MEMBER_TYPE", "SELF");

        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId("updateworkflow_" + participantIdSeed);
        return ParticipantDataTestUtil.createParticipantData(ddpParticipantId, participantDataMap,
                memberTypeFieldTypeId, ddpInstanceDto.getDdpInstanceId(), userDto.getEmailOrThrow());
    }

    private static void createMemberTypeFieldSetting() {
        FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(ddpInstanceDto.getDdpInstanceId())
                .withFieldType(memberTypeFieldTypeId)
                .withColumnName(MEMBER_TYPE).build();
        int fieldSettingsId = FieldSettingsDao.of().create(fieldSettingsDto);
        fieldSettingsIds.add(fieldSettingsId);
    }

    @Test
    public void testUpdateCustomWorkflow() {
        ParticipantData participantData = createParticipantData("ucf");
        int participantDataId = participantData.getParticipantDataId();
        String participantId = participantData.getDdpParticipantId().orElseThrow();

        String messageData = String.format("{\"participantGuid\":\"%s\",\"instanceName\":\"%s\","
                    + "\"data\":{\"workflow\":\"%s\",\"status\":\"COMPLETED\"}}",
                    participantId, instanceName, MEMBER_TYPE);

        JsonObject messageJsonObject = new Gson().fromJson(messageData, JsonObject.class);
        String dataString = messageJsonObject.get("data").getAsJsonObject().toString();
        Map<String, String> attributeMap = EditParticipantPublisherRoute.getStringStringMap("TEST", messageJsonObject);
        WorkflowStatusUpdate.updateCustomWorkflow(attributeMap, dataString);
        String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElseThrow();
        JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
        //checking that value was updated
        Assert.assertEquals("COMPLETED", dataJsonObject.get("MEMBER_TYPE").getAsString());
        //checking that updated value did not remove other fields
        Assert.assertEquals("REGISTERED", dataJsonObject.get("REGISTRATION_STATUS").getAsString());
    }

    @Test
    public void testUpdateWorkflowStatus() {
        ParticipantData participantData = createParticipantData("ups");
        int participantDataId = participantData.getParticipantDataId();

        String workflow = "REGISTRATION_STATUS";
        String status = "ENROLLED";
        String fieldType = participantData.getFieldTypeId().orElseThrow();
        boolean updated = WorkflowStatusUpdate.updateWorkflowStatus(workflow, status, participantData, fieldType);
        Assert.assertTrue(updated);
        String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElseThrow();
        JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
        Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
    }

    @Test
    public void testCreateParticipantData() {
        int participantDataId = -1;
        String workflow = "REGISTRATION_TYPE";
        String status = "SELF2";
        participantDataId = WorkflowStatusUpdate.createParticipantData(workflow, status,
                TestParticipantUtil.genDDPParticipantId("UpdateWorkflowStatusTest_anp"),
                ddpInstanceDto.getDdpInstanceId(), memberTypeFieldTypeId);
        String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElse("");
        JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
        Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
    }

    @Test
    public void testUpdateWorkflowData() {
        ParticipantDto participant = createParticipant();
        String ddpParticipantId = participant.getDdpParticipantIdOrThrow();
        String fieldTypeId = "AT_GROUP_MISCELLANEOUS";
        fieldSettingsIds.add(FieldSettingsTestUtil.createRegistrationStatusFieldSetting(fieldTypeId,
                ddpInstanceDto.getDdpInstanceId()));
        DDPInstance ddpInstance = DDPInstance.getDDPInstance(instanceName);

        try {
            // first time, no existing ptp data
            WorkflowStatusUpdate.updateWorkflowData(participant.getDdpParticipantIdOrThrow(), "REGISTRATION_STATUS",
                    "Registered", ddpInstance);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from updateWorkflowData: " + e.getMessage());
        }

        verifyWorkflowParticipantData(ddpParticipantId, fieldTypeId, "Registered", new ArrayList<>());
        verifyDefaultElasticData(ddpParticipantId, fieldTypeId, "Registered", new ArrayList<>());

        fieldSettingsIds.add(FieldSettingsTestUtil.createExitStatusFieldSetting(ddpInstanceDto.getDdpInstanceId()));

        try {
            // add default values
            boolean updated = ATParticipantDataService.generateDefaultData(instanceName, ddpParticipantId);
            Assert.assertTrue(updated);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from generateDefaults: " + e.getMessage());
        }

        List<String> otherFieldTypes = List.of(AT_GROUP_GENOME_STUDY, AT_PARTICIPANT_EXIT);
        verifyWorkflowParticipantData(ddpParticipantId, fieldTypeId, "Registered", otherFieldTypes);
        verifyDefaultElasticData(ddpParticipantId, fieldTypeId, "Registered", otherFieldTypes);

        try {
            // registration update, ptp data already exists
            WorkflowStatusUpdate.updateWorkflowData(participant.getDdpParticipantIdOrThrow(), "REGISTRATION_STATUS",
                    "Consented", ddpInstance);
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail("Exception from updateWorkflowData: " + e.getMessage());
        }

        verifyWorkflowParticipantData(ddpParticipantId, fieldTypeId, "Consented", otherFieldTypes);
        verifyDefaultElasticData(ddpParticipantId, fieldTypeId, "Consented", otherFieldTypes);
    }

    private ParticipantDto createParticipant() {
        String baseName = String.format("%s_%d", basename, participantCounter++);
        String ddpParticipantId = TestParticipantUtil.genDDPParticipantId(baseName);
        ParticipantDto participant = TestParticipantUtil.createParticipant(ddpParticipantId, ddpInstanceDto.getDdpInstanceId());
        participants.add(participant);

        ElasticTestUtil.createParticipant(esIndex, participant);
        ElasticTestUtil.addParticipantProfileFromFile(esIndex, "elastic/participantProfile.json",
                ddpParticipantId);
        List<Activities> activities = ParticipantDataTestUtil.getRgpActivities();
        ElasticTestUtil.addParticipantActivities(esIndex, activities, ddpParticipantId);

        return participant;
    }

    private void verifyWorkflowParticipantData(String ddpParticipantId, String registrationFieldTypeId,
                                               String registrationStatus, List<String> otherFieldTypes) {
        ParticipantDataDao dataDao = new ParticipantDataDao();
        List<ParticipantData> ptpDataList = dataDao.getParticipantData(ddpParticipantId);
        Assert.assertEquals(otherFieldTypes.size() + 1, ptpDataList.size());

        ptpDataList.forEach(ptpData -> {
            String fieldType = ptpData.getRequiredFieldTypeId();
            Map<String, String> dataMap = ptpData.getDataMap();
            if (fieldType.equals(registrationFieldTypeId)) {
                Assert.assertEquals(registrationStatus, dataMap.get("REGISTRATION_STATUS"));
            } else if (!otherFieldTypes.contains(fieldType)) {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }

    private void verifyDefaultElasticData(String ddpParticipantId, String registrationFieldTypeId,
                                          String registrationStatus, List<String> otherFieldTypes) {
        ElasticSearchParticipantDto esParticipant =
                ElasticSearchUtil.getParticipantESDataByParticipantId(esIndex, ddpParticipantId);
        log.debug("Verifying ES participant record for {}: {}", ddpParticipantId,
                ElasticTestUtil.getParticipantDocumentAsString(esIndex, ddpParticipantId));
        Dsm dsm = esParticipant.getDsm().orElseThrow();

        List<ParticipantData> participantDataList = dsm.getParticipantData();
        Assert.assertEquals(otherFieldTypes.size() + 1, participantDataList.size());

        participantDataList.forEach(participantData -> {
            String fieldType = participantData.getRequiredFieldTypeId();
            if (participantData.getRequiredFieldTypeId().equals(registrationFieldTypeId)) {
                Assert.assertEquals(ddpParticipantId, participantData.getRequiredDdpParticipantId());
                Assert.assertEquals(registrationStatus, participantData.getDataMap().get("REGISTRATION_STATUS"));
            } else if (!otherFieldTypes.contains(fieldType)) {
                Assert.fail("Unexpected field type: " + fieldType);
            }
        });
    }
}

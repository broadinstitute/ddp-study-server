package org.broadinstitute.dsm.pubsub;

import static org.broadinstitute.dsm.pubsub.WorkflowStatusUpdate.MEMBER_TYPE;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.DbTxnBaseTest;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.route.EditParticipantPublisherRoute;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

@Slf4j
public class UpdateWorkflowStatusTest extends DbTxnBaseTest {

    public static final String UPDATE_WORKFLOW_TEST = "UpdateWorkflowTest";
    public static final String fieldTypeId = UPDATE_WORKFLOW_TEST;

    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();
    private static final UserDao userDao = new UserDao();
    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();
    private static Gson gson;
    private static UserDto userDto;
    private static DDPInstanceDto ddpInstanceDto;
    private static String instanceName;
    private static int fieldSettingsId;

    @BeforeClass
    public static void setup() {
        gson = new Gson();

        instanceName = String.format("%s_%d", UPDATE_WORKFLOW_TEST, Instant.now().toEpochMilli());
        ddpInstanceDto = DBTestUtil.createTestDdpInstance(ddpInstanceDao, instanceName);
        userDto = DBTestUtil.createTestDsmUser("UpdateWorkflowUser", "UpdateWorkflow@status.com", userDao, userDto);

        createFieldSettings();
    }

    @AfterClass
    public static void tearDown() {
        if (fieldSettingsId > 0) {
            fieldSettingsDao.delete(fieldSettingsId);
        }
        userDao.delete(userDto.getId());
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
    }

    private static ParticipantData createParticipantData(String participantIdSeed) {
        Map<String, String> participantDataMap = new HashMap<>();
        participantDataMap.put("REGISTRATION_STATUS", "REGISTERED");
        participantDataMap.put("MEMBER_TYPE", "SELF");

        String participantId = genParticipantId(participantIdSeed);
        ParticipantData participantData = new ParticipantData.Builder()
                .withDdpParticipantId(participantId).withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                .withFieldTypeId(fieldTypeId).withData(gson.toJson(participantDataMap))
                .withLastChanged(System.currentTimeMillis()).withChangedBy(userDto.getEmail().orElse("")).build();

        participantData.setParticipantDataId(participantDataDao.create(participantData));
        return participantData;
    }

    private static String genParticipantId(String seed) {
        return String.format("WorkflowUpdateStatusTest_%s_%d", seed, Instant.now().toEpochMilli());
    }

    private static void deleteParticipantData(int participantDataId) {
        if (participantDataId >= 0) {
            participantDataDao.delete(participantDataId);
        }
    }

    private static void createFieldSettings() {
        FieldSettingsDto fieldSettingsDto = new FieldSettingsDto.Builder(ddpInstanceDto.getDdpInstanceId())
                .withFieldType(fieldTypeId)
                .withColumnName(MEMBER_TYPE).build();
        fieldSettingsId = FieldSettingsDao.of().create(fieldSettingsDto);
    }

    @Test
    public void testUpdateCustomWorkflow() {
        ParticipantData participantData = createParticipantData("ucf");
        int participantDataId = participantData.getParticipantDataId();
        String participantId = participantData.getDdpParticipantId().orElseThrow();
        try {
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
        } finally {
            deleteParticipantData(participantDataId);
        }
    }

    @Test
    public void testUpdateProbandStatusInDB() {
        ParticipantData participantData = createParticipantData("ups");
        int participantDataId = participantData.getParticipantDataId();
        try {
            String workflow = "REGISTRATION_STATUS";
            String status = "ENROLLED";
            String fieldType = participantData.getFieldTypeId().orElseThrow();
            WorkflowStatusUpdate.updateProbandStatusInDB(workflow, status, participantData, fieldType);
            String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElseThrow();
            JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
            Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
        } finally {
            deleteParticipantData(participantDataId);
        }
    }

    @Test
    public void testAddNewParticipantDataWithStatus() {
        int participantDataId = -1;
        try {
            String workflow = "REGISTRATION_TYPE";
            String status = "SELF2";
            participantDataId = WorkflowStatusUpdate.addNewParticipantDataWithStatus(workflow, status,
                    genParticipantId("anp"), ddpInstanceDto.getDdpInstanceId(), fieldTypeId);
            String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElse("");
            JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
            Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
        } finally {
            deleteParticipantData(participantDataId);
        }
    }
}

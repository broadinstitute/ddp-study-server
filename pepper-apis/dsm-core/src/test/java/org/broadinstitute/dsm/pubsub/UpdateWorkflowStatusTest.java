package org.broadinstitute.dsm.pubsub;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.settings.FieldSettingsDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.settings.FieldSettingsDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.broadinstitute.dsm.TestHelper.setupDB;

public class UpdateWorkflowStatusTest {

    public static final String UPDATE_WORKFLOW_TEST = "Update workflow test";
    public static final String RGP = "RGP";

    private static Gson gson;

    private static final String participantId = "RBMJW6ZIXVXBMXUX6M3Q";

    private static UserDto userDto;

    private static int participantDataId;

    private static ParticipantData participantData;

    private static final Map<String, String> testParticipantData = new HashMap<>();

    private static DDPInstanceDto ddpInstanceDto;
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();
    private static final FieldSettingsDao fieldSettingsDao = FieldSettingsDao.of();

    private static final UserDao userDao = new UserDao();

    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();

    @BeforeClass
    public static void doFirst() {
        setupDB();
        gson = new Gson();

        ddpInstanceDto =  DBTestUtil.createTestDdpInstance(ddpInstanceDto, ddpInstanceDao, "UpdateWorkflowInstance");

        userDto = DBTestUtil.createTestDsmUser("UpdateWorkflowUser", "UpdateWorkflow@status.com", userDao, userDto);

        createDataForParticipant();

        createParticipantData();

    }

    @Test
    public void testUpdateProbandStatusInDB() {
        String workflow = "REGISTRATION_STATUS";
        String status = "ENROLLED";
        WorkflowStatusUpdate.updateProbandStatusInDB(workflow, status, participantData, RGP);
        String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElse("");
        JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
        Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
    }

    @Test
    public void testAddNewParticipantDataWithStatus() {
        String workflow = "REGISTRATION_TYPE";
        String status = "SELF2";
        String ddpParticipantId = "RBMJW6ZIXVXBMXUX6M3W";
        Optional<FieldSettingsDto> fieldSetting = fieldSettingsDao
                .getFieldSettingByColumnNameAndInstanceId(16, workflow);
        if (fieldSetting.isPresent()) {
            int participantDataId = WorkflowStatusUpdate.addNewParticipantDataWithStatus(workflow, status, ddpParticipantId, fieldSetting.get());
            String data = participantDataDao.get(participantDataId).orElseThrow().getData().orElse("");
            JsonObject dataJsonObject = gson.fromJson(data, JsonObject.class);
            Assert.assertEquals(status, dataJsonObject.get(workflow).getAsString());
            participantDataDao.delete(participantDataId);
        }
    }

    private static void createDataForParticipant() {
        testParticipantData.put("REGISTRATION_STATUS", "REGISTERED");
        testParticipantData.put("MEMBER_TYPE", "SELF");
    }

    private static void createParticipantData() {
        participantData =
                new ParticipantData.Builder()
                    .withDdpParticipantId(participantId)
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withFieldTypeId(UPDATE_WORKFLOW_TEST)
                    .withData(gson.toJson(testParticipantData))
                    .withLastChanged(System.currentTimeMillis())
                    .withChangedBy(userDto.getEmail().orElse(""))
                    .build();
        participantDataId = participantDataDao.create(participantData);
        participantData =
                new ParticipantData.Builder()
                    .withParticipantDataId(participantDataId)
                    .withDdpParticipantId(participantId)
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withFieldTypeId(UPDATE_WORKFLOW_TEST)
                    .withData(gson.toJson(testParticipantData))
                    .withLastChanged(System.currentTimeMillis())
                    .withChangedBy(userDto.getEmail().orElse(""))
                    .build();
    }

    @AfterClass
    public static void finish() {
        if (participantDataId > 0) {
            participantDataDao.delete(participantDataId);
        }
        userDao.delete(userDto.getId());
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
    }
}

package org.broadinstitute.dsm.route.familymember;

import static org.broadinstitute.dsm.TestHelper.setupDB;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.handlers.util.Result;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.dao.ddp.instance.DDPInstanceDao;
import org.broadinstitute.dsm.db.dao.ddp.participant.ParticipantDataDao;
import org.broadinstitute.dsm.db.dao.user.UserDao;
import org.broadinstitute.dsm.db.dto.ddp.instance.DDPInstanceDto;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantData;
import org.broadinstitute.dsm.model.participant.data.AddFamilyMemberPayload;
import org.broadinstitute.dsm.model.participant.data.FamilyMemberDetails;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class AddFamilyMemberRouteTest {

    private static Gson gson;

    private static final String participantId = "RBMJW6ZIXVXBMXUX6M3Q";

    private static UserDto userDto;

    private static int ddpFamilyMemberParticipantDataId;
    private static int ddpExistingProbandParticipantDataId;
    private static int ddpCopiedProbandFamilyMemberParticipantDataId;

    private static final Map<String, String> familyMemberData = new HashMap<>();

    private static final Map<String, String> probandData = new HashMap<>();

    private static DDPInstanceDto ddpInstanceDto;
    private static final DDPInstanceDao ddpInstanceDao = new DDPInstanceDao();

    private static final UserDao userDao = new UserDao();

    private static final ParticipantDataDao participantDataDao = new ParticipantDataDao();

    @BeforeClass
    public static void doFirst() {
        setupDB();
        gson = new Gson();

        ddpInstanceDto =  DBTestUtil.createTestDdpInstance(ddpInstanceDto, ddpInstanceDao, "AddFamilyMemberInstance");

        userDto = DBTestUtil.createTestDsmUser("AddFamilyMemberUser", "addfamilymember@family.com", userDao, userDto);

        familyMemberData.putAll(new FamilyMemberDetails("Family", "Member", "Sister", 99, "PE3LHB_1_2").toMap());

        createProbandTestData();

        createProbandTestParticipantData();

    }

    private static void createProbandTestData() {
        Map<String, String> probandDetails =
                new FamilyMemberDetails("probandFirstName", "probandLastName", "Self", 99, "PE3LHB_1").toMap();
        probandData.put("ALIVE_DECEASED", "ALIVE");
        probandData.put("ACCEPTANCE_STATUS", "IN_REVIEW");
        probandData.put("ACTIVE", "HOLD");
        probandData.put("INACTIVE_REASON", "DECLINED");
        probandData.put("SEND_SECURE", "OPT_OUT");
        probandData.putAll(probandDetails);
    }

    private static void createProbandTestParticipantData() {
        ParticipantData probandParticipantData =
                new ParticipantData.Builder()
                    .withDdpParticipantId(participantId)
                    .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                    .withFieldTypeId(org.broadinstitute.dsm.model.participant.data.ParticipantData.FIELD_TYPE_PARTICIPANTS)
                    .withData(gson.toJson(probandData))
                    .withLastChanged(System.currentTimeMillis())
                    .withChangedBy(userDto.getEmail().orElse("SYSTEM"))
                    .build();
        ddpExistingProbandParticipantDataId = participantDataDao.create(probandParticipantData);
    }


    @AfterClass
    public static void finish() {
        if (ddpFamilyMemberParticipantDataId > 0) {
            participantDataDao.delete(ddpFamilyMemberParticipantDataId);
        }
        if (ddpExistingProbandParticipantDataId > 0) {
            participantDataDao.delete(ddpExistingProbandParticipantDataId);
        }
        if (ddpCopiedProbandFamilyMemberParticipantDataId > 0) {
            participantDataDao.delete(ddpCopiedProbandFamilyMemberParticipantDataId);
        }
        userDao.delete(userDto.getId());
        ddpInstanceDao.delete(ddpInstanceDto.getDdpInstanceId());
    }

    @Test
    public void noGuidProvided() {
        String payload = payloadFactory(null, ddpInstanceDto.getInstanceName(), familyMemberData, userDto.getId());
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        try {
            addFamilyMemberPayload.getParticipantId().orElseThrow(() -> new NoSuchElementException("Participant Guid is not provided"));
        } catch (NoSuchElementException nsee) {
            Assert.assertEquals("Participant Guid is not provided", nsee.getMessage());
        }
    }

    @Test
    public void noRealmProvided() {
        String payload = payloadFactory(participantId, null, familyMemberData, userDto.getId());
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        try {
            addFamilyMemberPayload.getRealm().orElseThrow(() -> new NoSuchElementException("Realm is not provided"));
        } catch (NoSuchElementException nsee) {
            Assert.assertEquals("Realm is not provided", nsee.getMessage());
        }
    }

    @Test
    public void noFamilyMemberDataProvided(){
        String payload = payloadFactory(participantId, ddpInstanceDto.getInstanceName(), Map.of(), userDto.getId());
        Result res = new Result(200);
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        if (addFamilyMemberPayload.getData().isEmpty() || addFamilyMemberPayload.getData().orElseGet(FamilyMemberDetails::new).isFamilyMemberFieldsEmpty()) {
            res = new Result(400, "Family member information is not provided");
        }
        Assert.assertEquals(400, res.getCode());
        Assert.assertEquals("Family member information is not provided", res.getBody());
    }

    @Test
    public void noUserIdProvided() {
        String payload = payloadFactory(participantId, ddpInstanceDto.getInstanceName(), Map.of(), null);
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        try {
            addFamilyMemberPayload.getUserId().orElseThrow(() -> new NoSuchElementException("User id is not provided"));
        } catch (NoSuchElementException nsee) {
            Assert.assertEquals("User id is not provided", nsee.getMessage());
        }
    }

    @Test
    public void relationshipIdAlreadyExists() {
        String payload = payloadFactory(participantId, ddpInstanceDto.getInstanceName(), probandData, userDto.getId());
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        org.broadinstitute.dsm.model.participant.data.ParticipantData participantData = new org.broadinstitute.dsm.model.participant.data.ParticipantData(participantDataDao);
        participantData.setData(addFamilyMemberPayload.getParticipantId().get(), ddpInstanceDto.getDdpInstanceId(),
                ddpInstanceDto.getInstanceName() + org.broadinstitute.dsm.model.participant.data.ParticipantData.FIELD_TYPE_PARTICIPANTS, probandData);
        Assert.assertTrue(participantData.isRelationshipIdExists());
    }

    @Test
    public void addFamilyMemberToParticipant() {
        String payload = payloadFactory(participantId, ddpInstanceDto.getInstanceName(), familyMemberData, userDto.getId());
        Result result = new Result(200);
        AddFamilyMemberPayload addFamilyMemberPayload = gson.fromJson(payload, AddFamilyMemberPayload.class);
        try {
            ParticipantData participantData =
                    new ParticipantData.Builder()
                        .withDdpParticipantId(addFamilyMemberPayload.getParticipantId().get())
                        .withDdpInstanceId(ddpInstanceDto.getDdpInstanceId())
                        .withFieldTypeId(ddpInstanceDto.getInstanceName() + org.broadinstitute.dsm.model.participant.data.ParticipantData.FIELD_TYPE_PARTICIPANTS)
                        .withData(gson.toJson(addFamilyMemberPayload.getData().get()))
                        .withLastChanged(System.currentTimeMillis())
                        .withChangedBy(userDto.getEmail().orElse("SYSTEM"))
                        .build();
            ddpFamilyMemberParticipantDataId = participantDataDao.create(participantData);
        } catch (Exception e) {
            result = new Result(500);
        }
        Assert.assertEquals(200, result.getCode());
    }



    public static String payloadFactory(String participantGuid, String realm, Map<String, String> data, Integer userId) {
        FamilyMemberDetails familyMemberDetails = null;
        if (data != null) {
            try {
                familyMemberDetails = new FamilyMemberDetails(
                    //reflection applied here, to match field namesm because FamilyMemberDetails toMap method uses reflection as well
                    data.get(FamilyMemberDetails.class.getDeclaredField("firstName").getAnnotation(SerializedName.class).value()),
                    data.get(FamilyMemberDetails.class.getDeclaredField("lastName").getAnnotation(SerializedName.class).value()),
                    data.get(FamilyMemberDetails.class.getDeclaredField("memberType").getAnnotation(SerializedName.class).value()),
                    Long.parseLong(data.get(FamilyMemberDetails.class.getDeclaredField("familyId").getAnnotation(SerializedName.class).value())),
                    data.get(FamilyMemberDetails.class.getDeclaredField("collaboratorParticipantId").getAnnotation(SerializedName.class).value())
                );
            } catch (Exception e) {
                Assert.fail();
            }
        }
        AddFamilyMemberPayload addFamilyMemberPayload = new AddFamilyMemberPayload.Builder(participantGuid, realm)
                .withData(familyMemberDetails)
                .withUserId(userId)
                .withCopyProbandInfo(false)
                .withProbandDataId(0)
                .build();

        return gson.toJson(addFamilyMemberPayload);
    }

}
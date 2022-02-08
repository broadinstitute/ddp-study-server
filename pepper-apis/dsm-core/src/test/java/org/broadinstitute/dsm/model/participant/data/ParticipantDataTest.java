package org.broadinstitute.dsm.model.participant.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import com.google.gson.Gson;
import org.broadinstitute.dsm.db.dto.ddp.participant.ParticipantDataDto;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class ParticipantDataTest {

    private static final String[] MEMBER_TYPES = {"SELF", "SISTER", "SON", "MOTHER", "FATHER", "COUSIN"};
    private static final Gson GSON = new Gson();
    private static ParticipantData participantData;

    @BeforeClass
    public static void setUp() {
        participantData = new ParticipantData();
    }

    @Test
    public void testFindProband() {
        List<ParticipantDataDto> participantDataDtoList = generateParticipantData();
        Optional<ParticipantDataDto> maybeProbandData = participantData.findProband(participantDataDtoList);
        Map<String, String> map = GSON.fromJson(maybeProbandData.flatMap(ParticipantDataDto::getData).orElse(""), Map.class);
        Assert.assertEquals("SELF", map.get("MEMBER_TYPE"));
    }

    @Test
    public void testGetFamilyId() {
        List<ParticipantDataDto> participantDataDtoList = generateParticipantData();
        ParticipantDataDto maybeProbandData = participantData.findProband(participantDataDtoList).get();
        maybeProbandData.setData("{\"FAMILY_ID\":\"2005\"}");
        AddFamilyMemberPayload addFamilyMemberPayload = new AddFamilyMemberPayload.Builder("", "").build();
        long familyId = 0;
        try {
            familyId = addFamilyMemberPayload.getFamilyId(participantDataDtoList);
        } catch (NoSuchFieldException e) {
            Assert.fail();
        }
        Assert.assertEquals(2005, familyId);
    }

    @Test
    public void testFamilyMemberHasNotApplicantEmail() {
        ParticipantData participantData = new ParticipantData(0, "", 0, "", Map.of(FamilyMemberConstants.EMAIL, "familymember@mail.com"));
        Assert.assertFalse(participantData.hasFamilyMemberApplicantEmail());
    }

    private List<ParticipantDataDto> generateParticipantData() {
        Random random = new Random();
        List<ParticipantDataDto> participantDataDtoList = new ArrayList<>();
        for (int i = 0; i < MEMBER_TYPES.length; i++) {
            String memberType = MEMBER_TYPES[i];
            int randomGeneratedFamilyId = random.nextInt();
            long familyId = random.nextInt(1000) + 1;
            String collaboratorParticipantId = "STUDY" + "_" + familyId + "_" + ("SELF".equals(memberType) ? 3 :
                    randomGeneratedFamilyId == 3 ? randomGeneratedFamilyId + 1 : randomGeneratedFamilyId);
            String email = "SELF".equals(memberType) ? "self@mail.com" : MEMBER_TYPES[1 + random.nextInt(MEMBER_TYPES.length - 1)] +
                    "@mail.com";
            FamilyMemberDetails familyMemberDetails = new FamilyMemberDetails(
                    "John" + i,
                    "Doe" + i,
                    memberType,
                    familyId,
                    collaboratorParticipantId);
            familyMemberDetails.setEmail(email);
            String data = GSON.toJson(familyMemberDetails);
            ParticipantDataDto participantDataDto =
                    new ParticipantDataDto.Builder()
                            .withDdpParticipantId(collaboratorParticipantId)
                            .withDdpInstanceId(i)
                            .withFieldTypeId("")
                            .withData(data)
                            .withLastChanged(System.currentTimeMillis())
                            .withChangedBy("SYSTEM")
                            .build();
            participantDataDtoList.add(participantDataDto);
        }
        return participantDataDtoList;
    }


}

package org.broadinstitute.dsm.model.participant.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.participant.data.ParticipantDataDto;

@Data
public class NewParticipantData {

    private long dataId;
    private String ddpParticipantId;
    private int ddpInstanceId;
    private String fieldTypeId;
    private Map<String, String> data;

    private Dao dataAccess;

    public NewParticipantData() {}

    public NewParticipantData(Dao dao) {
        dataAccess = dao;
    }

    public NewParticipantData(long participantDataId, String ddpParticipantId, int ddpInstanceId, String fieldTypeId,
                              Map<String, String> data) {
        this.dataId = participantDataId;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
    }

    public static NewParticipantData parseDto(@NonNull ParticipantDataDto participantDataDto) {
        return new NewParticipantData(
                participantDataDto.getParticipantDataId(),
                participantDataDto.getDdpParticipantId(),
                participantDataDto.getDdpInstanceId(),
                participantDataDto.getFieldTypeId(),
                new Gson().fromJson(participantDataDto.getData(), new TypeToken<Map<String, String>>() {}.getType())
        );
    }

    public static List<NewParticipantData> parseDtoList(@NonNull List<ParticipantDataDto> participantDataDtoList) {
        List<NewParticipantData> participantData = new ArrayList<>();
        participantDataDtoList.forEach(dto -> participantData.add(new NewParticipantData(
                dto.getParticipantDataId(),
                dto.getDdpParticipantId(),
                dto.getDdpInstanceId(),
                dto.getFieldTypeId(),
                new Gson().fromJson(dto.getData(), new TypeToken<Map<String, String>>() {}.getType())
        )));
        return participantData;
    }

    public Map<String, String> mergeParticipantData(@NonNull AddFamilyMemberPayload familyMemberPayload) {
        FamilyMemberDetails familyMemberData =
                familyMemberPayload.getData().orElseThrow(() -> new NoSuchElementException("Family member data is not provided"));
        Map<String, String> mergedData = new HashMap<>();
        boolean copyProbandInfo = familyMemberPayload.getCopyProbandInfo().orElse(Boolean.FALSE);
        int probandDataId = familyMemberPayload.getProbandDataId().orElse(0);
        if (copyProbandInfo && probandDataId > 0) {
            Optional<NewParticipantData> maybeParticipantData = dataAccess.get(probandDataId).map(pd -> parseDto((ParticipantDataDto)pd));
            maybeParticipantData.ifPresent(p -> mergedData.putAll(p.getData()));
        }
        //after self/proband data has been put into mergedData, to replace self/proband's [FIRSTNAME, LASTNAME, MEMBER_TYPE...] values by new family member's data
        mergedData.putAll(familyMemberData.toMap());
        return mergedData;
    }

    public void setData(String ddpParticipantId, int ddpInstanceId, String fieldTypeId, Map<String, String> data) {
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
    }

    public void insertParticipantData(String userEmail) {
        ParticipantDataDto participantDataDto =
                new ParticipantDataDto(this.ddpParticipantId, this.ddpInstanceId, this.fieldTypeId, new Gson().toJson(this.data),
                        System.currentTimeMillis(), userEmail);
        int createdDataKey = dataAccess.create(participantDataDto);
        if (createdDataKey < 1) {
            throw new RuntimeException("Could not insert participant data for : " + this.ddpParticipantId);
        }
    }
}

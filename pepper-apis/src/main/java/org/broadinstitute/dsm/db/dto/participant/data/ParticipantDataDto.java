package org.broadinstitute.dsm.db.dto.participant.data;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParticipantDataDto {

    private int participantDataId;
    private String ddpParticipantId;
    private int ddpInstanceId;
    private String fieldTypeId;
    private String data;
    private long lastChanged;
    private String changedBy;

    public ParticipantDataDto(String ddpParticipantId, int ddpInstanceId, String fieldTypeId, String data, long lastChanged,
                              String changedBy) {
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
        this.fieldTypeId = fieldTypeId;
        this.data = data;
        this.lastChanged = lastChanged;
        this.changedBy = changedBy;
    }

    public ParticipantDataDto(int participantDataId, String ddpParticipantId, int ddpInstanceId,
                              String fieldTypeId, String data, long lastChanged, String changedBy) {
        this(ddpParticipantId, ddpInstanceId, fieldTypeId, data, lastChanged, changedBy);
        this.participantDataId = participantDataId;
    }


}


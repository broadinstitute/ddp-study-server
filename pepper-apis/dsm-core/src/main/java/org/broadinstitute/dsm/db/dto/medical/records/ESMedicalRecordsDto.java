package org.broadinstitute.dsm.db.dto.medical.records;

import lombok.Data;

@Data
public class ESMedicalRecordsDto {
    private String ddpParticipantId;
    private Integer medicalRecordId;
    private String name;
    private String type;
    private String requested;
    private String received;

    public ESMedicalRecordsDto(String ddpParticipantId, Integer medicalRecordId, String name, String type, String requested, String received) {
        this.ddpParticipantId = ddpParticipantId;
        this.medicalRecordId = medicalRecordId;
        this.name = name;
        this.type = type;
        this.requested = requested;
        this.received = received;
    }
}

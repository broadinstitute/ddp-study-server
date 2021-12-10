package org.broadinstitute.dsm.db.dto.ddp.tissue;

import lombok.Data;

@Data
public class ESTissueRecordsDto {
    private String ddpParticipantId;
    private Integer tissueRecordId;
    private String typePx;
    private String locationPX;
    private String datePX;
    private String histology;
    private String accessionNumber;
    private String request;
    private String received;
    private String sent;

    public ESTissueRecordsDto(String ddpParticipantId, Integer tissueRecordId, String typePx, String locationPX, String datePX, String histology, String accessionNumber, String request, String received, String sent) {
        this.ddpParticipantId = ddpParticipantId;
        this.tissueRecordId = tissueRecordId;
        this.typePx = typePx;
        this.locationPX = locationPX;
        this.datePX = datePX;
        this.histology = histology;
        this.accessionNumber = accessionNumber;
        this.request = request;
        this.received = received;
        this.sent = sent;
    }
}

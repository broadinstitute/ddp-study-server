package org.broadinstitute.dsm.db.dto.ddp.kitrequest;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.sql.Date;
import java.sql.Timestamp;

@Data
public class KitRequestDto {

    private int dsmKitRequestId;
    private int ddpInstanceId;
    private String ddpKitRequestId;
    private int kitTypeId;
    private String bspCollaboratorParticipnatId;
    private String bspCollaboratorSampleId;
    private String ddpParticipantId;
    private String ddpLabel;
    private String createdBy;
    private long createdDate;
    private String externalOrderNumber;
    private long externalOrderDate;
    private String externalOrderStatus;
    private String externalResponse;
    private String uploadReason;
    private Timestamp orderTransmitted_at;

    public KitRequestDto(
             int dsmKitRequestId,
             int ddpInstanceId,
             String ddpKitRequestId,
             int kitTypeId,
             String bspCollaboratorParticipnatId,
             String bspCollaboratorSampleId,
             String ddpParticipantId,
             String ddpLabel,
             String createdBy,
             long createdDate,
             String externalOrderNumber,
             long externalOrderDate,
             String externalOrderStatus,
             String externalResponse,
             String uploadReason,
             Timestamp orderTransmitted_at) {
        this.dsmKitRequestId = dsmKitRequestId;
        this.ddpInstanceId = ddpInstanceId;
        this.ddpKitRequestId = ddpKitRequestId;
        this.kitTypeId = kitTypeId;
        this.bspCollaboratorParticipnatId = bspCollaboratorParticipnatId;
        this.bspCollaboratorSampleId = bspCollaboratorSampleId;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpLabel = ddpLabel;
        this.createdBy = createdBy;
        this.createdDate = createdDate;
        this.externalOrderNumber = externalOrderNumber;
        this.externalOrderDate = externalOrderDate;
        this.externalOrderStatus = externalOrderStatus;
        this.externalResponse = externalResponse;
        this.uploadReason = uploadReason;
        this.orderTransmitted_at = orderTransmitted_at;
    }

    public boolean hasUploadReason() {
        return StringUtils.isNotBlank(uploadReason);
    }
}

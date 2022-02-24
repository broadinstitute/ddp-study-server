package org.broadinstitute.dsm.db.dto.ddp.kitrequest;

import java.sql.Timestamp;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class KitRequestDto {

    private int dsmKitRequestId;
    private int ddpInstanceId;
    private String ddpKitRequestId;
    private int kitTypeId;
    private String bspCollaboratorParticipantId;
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
    private Timestamp orderTransmittedAt;

    public KitRequestDto(
            int dsmKitRequestId,
            int ddpInstanceId,
            String ddpKitRequestId,
            int kitTypeId,
            String bspCollaboratorParticipantId,
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
            Timestamp orderTransmittedAt) {
        this.dsmKitRequestId = dsmKitRequestId;
        this.ddpInstanceId = ddpInstanceId;
        this.ddpKitRequestId = ddpKitRequestId;
        this.kitTypeId = kitTypeId;
        this.bspCollaboratorParticipantId = bspCollaboratorParticipantId;
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
        this.orderTransmittedAt = orderTransmittedAt;
    }

    public boolean hasUploadReason() {
        return StringUtils.isNotBlank(uploadReason);
    }
}

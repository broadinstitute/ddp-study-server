package org.broadinstitute.dsm.db.dto.mercury;

import java.util.Optional;

import lombok.Data;

@Data

public class MercuryOrderDto {
    long mercurySequencingId;
    String ddpParticipantId;
    String orderId;
    String creatorId;
    String barcode;
    int kitTypeId;
    long orderDate;
    String orderStatus;
    long statusDate;
    String mercuryPdoId;
    String createdBy;
    int ddpInstanceId;
    Long tissueId;
    Long dsmKitRequestId;
    String statusDetail;
    String collaboratorSampleId;
    String sampleType;

    public MercuryOrderDto(String ddpParticipantId, String creatorId, String barcode, int kitTypeId, int ddpInstanceId, Long tissueId,
                           Long dsmKitRequestId) {
        this.ddpParticipantId = ddpParticipantId;
        this.creatorId = creatorId;
        this.barcode = barcode;
        this.kitTypeId = kitTypeId;
        this.ddpInstanceId = ddpInstanceId;
        this.tissueId = tissueId;
        this.dsmKitRequestId = dsmKitRequestId;
    }

    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }
}

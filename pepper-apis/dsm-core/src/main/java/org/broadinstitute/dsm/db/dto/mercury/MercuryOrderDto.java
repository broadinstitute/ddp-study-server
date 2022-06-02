package org.broadinstitute.dsm.db.dto.mercury;

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

    public MercuryOrderDto(String ddpParticipantId, String creatorId, String barcode, int kitTypeId) {
        this.ddpParticipantId = ddpParticipantId;
        this.creatorId = creatorId;
        this.barcode = barcode;
        this.kitTypeId = kitTypeId;
    }
}

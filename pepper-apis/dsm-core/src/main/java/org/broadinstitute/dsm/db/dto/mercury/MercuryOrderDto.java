package org.broadinstitute.dsm.db.dto.mercury;

import java.util.Optional;

import lombok.Data;

@Data

public class MercuryOrderDto {
    int mercurySequencingId;
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
    Integer tissueId;
    Integer dsmKitRequestId;
    String statusDetail;
    String collaboratorSampleId;
    String sampleType;

    public MercuryOrderDto(String ddpParticipantId, String creatorId, String barcode, int kitTypeId, int ddpInstanceId, Integer tissueId,
                           Integer dsmKitRequestId) {
        this.ddpParticipantId = ddpParticipantId;
        this.creatorId = creatorId;
        this.barcode = barcode;
        this.kitTypeId = kitTypeId;
        this.ddpInstanceId = ddpInstanceId;
        this.tissueId = tissueId;
        this.dsmKitRequestId = dsmKitRequestId;
    }

    public MercuryOrderDto(Builder builder) {
        this.mercurySequencingId = builder.mercurySequencingOrderId;
        this.orderId = builder.orderId;
        this.ddpParticipantId = builder.ddpParticipantId;
        this.orderDate = builder.orderDate;
        this.barcode = builder.barcode;
        this.ddpInstanceId = builder.ddpInstanceId;
        this.kitTypeId = builder.kitTypeId;
        this.orderStatus = builder.orderStatus;
        this.statusDate = builder.statusDate;
        this.mercuryPdoId = builder.mercuryPdoId;
        this.createdBy = builder.createdByUserId;
        this.tissueId = builder.tissueId;
        this.dsmKitRequestId = builder.dsmKitRequestId;
        this.statusDetail = builder.statusDetail;
    }

    public Optional<String> getCreatedBy() {
        return Optional.ofNullable(createdBy);
    }

    public static class Builder {
        int mercurySequencingOrderId;
        String orderId;
        String ddpParticipantId;
        long orderDate;
        String barcode;
        int ddpInstanceId;
        int kitTypeId;
        String orderStatus;
        long statusDate;
        String mercuryPdoId;
        String createdByUserId;
        Integer tissueId;
        Integer dsmKitRequestId;
        String statusDetail;

        public Builder withMercurySequencingOrderId(int mercurySequencingOrderId) {
            this.mercurySequencingOrderId = mercurySequencingOrderId;
            return this;
        }

        public Builder withKitTypeId(int kitTypeId) {
            this.kitTypeId = kitTypeId;
            return this;
        }

        public Builder withCreatedByUserId(String createdByUserId) {
            this.createdByUserId = createdByUserId;
            return this;
        }

        public Builder withTissueId(int tissueId) {
            this.tissueId = tissueId;
            return this;
        }

        public Builder withDsmKitRequestId(Integer dsmKitRequestId) {
            this.dsmKitRequestId = dsmKitRequestId;
            return this;
        }

        public Builder withDdpInstanceId(int ddpInstanceId) {
            this.ddpInstanceId = ddpInstanceId;
            return this;
        }

        public Builder withOrderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder withDdpParticipantId(String ddpParticipantId) {
            this.ddpParticipantId = ddpParticipantId;
            return this;
        }

        public Builder withOrderStatus(String orderStatus) {
            this.orderStatus = orderStatus;
            return this;
        }

        public Builder withMercuryPdoId(String mercuryPdoId) {
            this.mercuryPdoId = mercuryPdoId;
            return this;
        }

        public Builder withBarcode(String barcode) {
            this.barcode = barcode;
            return this;
        }

        public Builder withStatusDetail(String statusDetail) {
            this.statusDetail = statusDetail;
            return this;
        }

        public Builder withOrderDate(long orderDate) {
            this.orderDate = orderDate;
            return this;
        }

        public Builder withStatusDate(long statusDate) {
            this.statusDate = statusDate;
            return this;
        }

        public MercuryOrderDto build() {
            return new MercuryOrderDto(this);
        }

    }
}

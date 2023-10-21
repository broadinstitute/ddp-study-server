package org.broadinstitute.dsm.db;

import java.util.Optional;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.filter.postfilter.HasDdpInstanceId;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.DDP_MERCURY_SEQUENCING,
        alias = DBConstants.DDP_MERCURY_SEQUENCING_ALIAS,
        primaryKey = DBConstants.MERCURY_SEQUENCING_ID,
        columnPrefix = "")
public class ClinicalOrder implements HasDdpInstanceId {
    @ColumnName(DBConstants.MERCURY_SEQUENCING_ID)
    public String mercurySequencingId;

    @ColumnName(DBConstants.MERCURY_ORDER_ID)
    public String orderId;

    @ColumnName(DBConstants.DDP_PARTICIPANT_ID)
    public String ddpParticipantId;

    @ColumnName(DBConstants.MERCURY_ORDER_DATE)
    public long orderDate;

    @ColumnName(DBConstants.MERCURY_BARCODE)
    public String barcode;

    @ColumnName(DBConstants.DDP_INSTANCE_ID)
    public long ddpInstanceId;

    @ColumnName(DBConstants.KIT_TYPE_ID)
    public long kitTypeId;

    @ColumnName(DBConstants.MERCURY_ORDER_STATUS)
    public String orderStatus;

    @ColumnName(DBConstants.MERCURY_STATUS_DATE)
    public long statusDate;

    @ColumnName(DBConstants.MERCURY_PDO_ID)
    public String mercuryPdoId;

    @ColumnName(DBConstants.TISSUE_ID)
    public long tissueId;

    @ColumnName(DBConstants.DSM_KIT_REQUEST_ID)
    public long dsmKitRequestId;

    @ColumnName(DBConstants.MERCURY_STATUS_DETAIL)
    public String statusDetail;

    public ClinicalOrder(String mercurySequencingId, String orderId, String ddpParticipantId, Long orderDate,
                         long ddpInstanceId, String orderStatus, Long statusDate, String mercuryPdoId, Long tissueId,
                         Long dsmKitRequestId, String statusDetail) {
        this.mercurySequencingId = mercurySequencingId;
        this.orderId = orderId;
        this.orderDate = orderDate;
        this.orderStatus = orderStatus;
        if (StringUtils.isBlank(orderStatus)) {
            this.orderStatus = "Sent";
        }
        this.statusDate = statusDate;
        this.mercuryPdoId = mercuryPdoId;
        this.tissueId = tissueId;
        this.dsmKitRequestId = dsmKitRequestId;
        this.statusDetail = statusDetail;
        this.ddpParticipantId = ddpParticipantId;
        this.ddpInstanceId = ddpInstanceId;
    }

    public ClinicalOrder() {

    }

    public ClinicalOrder(long ddpInstanceId) {
        this.ddpInstanceId = ddpInstanceId;
    }

    @Override
    public Optional<Long> extractDdpInstanceId() {
        return Optional.of(getDdpInstanceId());
    }
}

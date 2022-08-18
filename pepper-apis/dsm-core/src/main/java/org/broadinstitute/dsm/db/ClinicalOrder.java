package org.broadinstitute.dsm.db;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.DDP_MERCURY_SEQUENCING,
        alias = DBConstants.DDP_MERCURY_SEQUENCING_ALIAS,
        primaryKey = DBConstants.MERCURY_SEQUENCING_ID,
        columnPrefix = "")
@AllArgsConstructor
public class ClinicalOrder {
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



}

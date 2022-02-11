package org.broadinstitute.dsm.model;

import lombok.Data;
import org.broadinstitute.dsm.db.structure.ColumnName;
import org.broadinstitute.dsm.db.structure.DbDateConversion;
import org.broadinstitute.dsm.db.structure.SqlDateConverter;
import org.broadinstitute.dsm.db.structure.TableName;
import org.broadinstitute.dsm.model.ddp.DDPParticipant;
import org.broadinstitute.dsm.statics.DBConstants;

@Data
@TableName(
        name = DBConstants.DDP_KIT_REQUEST,
        alias = DBConstants.DDP_KIT_REQUEST_ALIAS,
        primaryKey = "",
        columnPrefix = "")
public class KitRequest {

    @ColumnName(DBConstants.DSM_KIT_REQUEST_ID)
    private Long dsmKitRequestId;
    private String participantId;
    private String shortId;
    @ColumnName(DBConstants.DSM_LABEL)
    private String ddpLabel;
    private DDPParticipant participant;

    @ColumnName(DBConstants.EXTERNAL_ORDER_STATUS)
    private String externalOrderStatus;
    private String externalKitName;

    @ColumnName(DBConstants.EXTERNAL_ORDER_NUMBER)
    private String externalOrderNumber;

    @ColumnName (DBConstants.EXTERNAL_ORDER_DATE)
    @DbDateConversion(SqlDateConverter.EPOCH)
    private Long externalOrderDate;

    public KitRequest() {}

    public KitRequest(String participantId, String shortId, DDPParticipant participant, String externalOrderNumber) {
        this(null, participantId, shortId, null, externalOrderNumber, participant, null, null, null);
    }

    public KitRequest(Long dsmKitRequestId, String participantId, String shortId, String ddpLabel, String externalOrderNumber,
                      DDPParticipant participant, String externalOrderStatus, String externalKitName, Long externalOrderDate) {
        this.dsmKitRequestId = dsmKitRequestId;
        this.participantId = participantId;
        this.shortId = shortId;
        this.ddpLabel = ddpLabel;
        this.externalOrderNumber = externalOrderNumber;
        this.participant = participant;
        this.externalOrderStatus = externalOrderStatus;
        this.externalKitName = externalKitName;
        this.externalOrderDate = externalOrderDate;
    }
}

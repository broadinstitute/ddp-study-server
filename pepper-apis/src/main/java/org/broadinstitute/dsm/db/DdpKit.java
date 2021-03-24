package org.broadinstitute.dsm.db;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.ups.UPSStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

@Data
public class DdpKit {
    String DsmKitRequestId;
    String kitLabel;
    String trackingToId;
    String trackingReturnId;
    String error;
    String message;
    String receiveDate;
    String upsTrackingStatus;
    String upsTrackingDate;
    String upsReturnStatus;
    String upsReturnDate;
    String HRUID;
    String externalOrderNumber;
    boolean CEOrdered;
    private static final Logger logger = LoggerFactory.getLogger(DdpKit.class);

    public DdpKit(String DsmKitRequestId, String kitLabel, String trackingToId, String trackingReturnId, String error,
                  String message, String receiveDate, String upsTrackingStatus, String upsTrackingDate,
                  String upsReturnStatus, String upsReturnDate, String bspCollaboratodId, String externalOrderNumber,
                  boolean CEOrdered) {
        this.DsmKitRequestId = DsmKitRequestId;
        this.kitLabel = kitLabel;
        this.trackingToId = trackingToId;
        this.trackingReturnId = trackingReturnId;
        this.error = error;
        this.message = message;
        this.receiveDate = receiveDate;
        this.upsTrackingStatus = upsTrackingStatus;
        this.upsTrackingDate = upsTrackingDate;
        this.upsReturnStatus = upsReturnStatus;
        this.upsReturnDate = upsReturnDate;
        this.HRUID = bspCollaboratodId;
        this.externalOrderNumber = externalOrderNumber;
        this.CEOrdered = CEOrdered;
    }

    public void changeCEOrdered(Connection conn, boolean orderStatus) {
        String query = "UPDATE ddp_kit SET CE_order = ? where dsm_kit_request_id = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setBoolean(1, orderStatus);
            stmt.setString(2, this.getDsmKitRequestId());
            int r = stmt.executeUpdate();
            if (r != 1) {//number of subkits
                throw new RuntimeException("Update query for CE order flag updated " + r + " rows! with dsm kit request id: " + this.getDsmKitRequestId());
            }
            logger.info("Updated CE_Order value for kit with dsm kit request id " + this.getDsmKitRequestId()
                    + " to " + orderStatus);
        }
        catch (Exception e) {
            throw new RuntimeException("Could not update ce_ordered status for " + this.getDsmKitRequestId(),e);
        }
    }

    public boolean isDelivered() {
        if (StringUtils.isNotBlank(trackingToId)) {
            return isTypeDelivered(upsTrackingStatus);
        }
        return false;
    }

    public boolean isReturned() {
        if (StringUtils.isNotBlank(trackingReturnId)) {
            return isTypeDelivered(upsReturnStatus);
        }
        return false;
    }

    private boolean isTypeDelivered(String statusDescription) {
        if (StringUtils.isNotBlank(statusDescription) && statusDescription.indexOf(' ') > -1) {// get only type from it
            String type = statusDescription.substring(0, statusDescription.indexOf(' '));
            return UPSStatus.DELIVERED_TYPE.equals(type);
        }
        return false;
    }

    public String getMainKitLabel() {
        if (StringUtils.isNotBlank(kitLabel) && kitLabel.contains("_1") && kitLabel.indexOf("_1") == kitLabel.length() - 2) {
            return kitLabel.substring(0, kitLabel.length() - 2);
        }
        return kitLabel;
    }

}

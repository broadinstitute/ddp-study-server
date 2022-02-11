package org.broadinstitute.dsm.model.ups;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;

@Data
public class UPSKit {
    // the two shipments differ in tracking id
    UPSPackage upsPackage; // package with tracking_to_id
    String kitLabel;
    Boolean CE_order;
    String dsmKitRequestId;
    String externalOrderNumber;
    String trackingToId;
    String trackingReturnId;
    String ddpInstanceId;
    String hruid;
    boolean gbfShippedTriggerDSSDelivered;

    private static final Logger logger = LoggerFactory.getLogger(UPSKit.class.getName());

    public UPSKit(@NonNull UPSPackage upsPackage, String kitLabel, Boolean CE_order, String dsmKitRequestId, String externalOrderNumber,
                  String trackingToId, String trackingReturnId, String ddpInstanceId, String hruid, boolean gbfShippedTriggerDSSDelivered) {
        this.upsPackage = upsPackage;
        this.kitLabel = kitLabel;
        this.CE_order = CE_order;
        this.dsmKitRequestId = dsmKitRequestId;
        this.externalOrderNumber = externalOrderNumber;
        this.trackingToId = trackingToId;
        this.trackingReturnId = trackingReturnId;
        this.ddpInstanceId = ddpInstanceId;
        this.hruid = hruid;
        if (StringUtils.isNotBlank(hruid) && hruid.contains("_")) {
            this.hruid = hruid.substring(hruid.indexOf("_") + 1);
        }
        this.gbfShippedTriggerDSSDelivered = gbfShippedTriggerDSSDelivered;
    }

    public boolean isReturn() {
        if (this.upsPackage != null && this.upsPackage.getTrackingNumber() != null && trackingReturnId != null) {
            return upsPackage.getTrackingNumber().equals(trackingReturnId);
        }
        else {
            throw new RuntimeException("Couldn't say if the package was return, either upsPackage or tracking number was null  for  " + getDsmKitRequestId());
        }
    }

    public String getMainKitLabel() {
        if (StringUtils.isNotBlank(kitLabel) && kitLabel.contains("_1") && kitLabel.indexOf("_1") == kitLabel.length() - 2) {
            return kitLabel.substring(0, kitLabel.length() - 2);
        }
        return kitLabel;
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
            conn.commit();
        }
        catch (Exception e) {
            throw new RuntimeException("Could not update ce_ordered status for " + this.getDsmKitRequestId(), e);
        }
    }

}

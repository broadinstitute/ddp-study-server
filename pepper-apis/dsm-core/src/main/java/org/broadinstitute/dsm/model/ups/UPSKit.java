package org.broadinstitute.dsm.model.ups;

import java.sql.Connection;
import java.sql.PreparedStatement;

import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class UPSKit {
    private static final Logger logger = LoggerFactory.getLogger(UPSKit.class.getName());
    // the two shipments differ in tracking id
    UPSPackage upsPackage; // package with tracking_to_id
    String kitLabel;
    Boolean ceOrder;
    String dsmKitRequestId;
    String externalOrderNumber;
    String trackingToId;
    String trackingReturnId;
    String ddpInstanceId;
    String hruid;
    boolean gbfShippedTriggerDSSDelivered;

    public UPSKit(@NonNull UPSPackage upsPackage, String kitLabel, Boolean ceOrder, String dsmKitRequestId, String externalOrderNumber,
                  String trackingToId, String trackingReturnId, String ddpInstanceId, String hruid, boolean gbfShippedTriggerDSSDelivered) {
        this.upsPackage = upsPackage;
        this.kitLabel = kitLabel;
        this.ceOrder = ceOrder;
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
        } else {
            throw new RuntimeException(
                    "Couldn't say if the package was return, either upsPackage or tracking number was null  for  " + getDsmKitRequestId());
        }
    }

    public String getMainKitLabel() {
        if (StringUtils.isNotBlank(kitLabel) && kitLabel.contains("_1") && kitLabel.indexOf("_1") == kitLabel.length() - 2) {
            return kitLabel.substring(0, kitLabel.length() - 2);
        }
        return kitLabel;
    }
}

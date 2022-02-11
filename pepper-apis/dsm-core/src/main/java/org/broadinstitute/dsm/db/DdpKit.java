package org.broadinstitute.dsm.db;

import lombok.Data;
import org.broadinstitute.dsm.model.ups.UPSShipment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

@Data
public class DdpKit {
    String dsmKitRequestId;
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
    String ddpInstanceId;
    UPSShipment shipment;

    private static final Logger logger = LoggerFactory.getLogger(DdpKit.class);

    public DdpKit(String dsmKitRequestId, String kitLabel, String trackingToId, String trackingReturnId, String error,
                  String message, String receiveDate, String bspCollaboratodId, String externalOrderNumber,
                  boolean CEOrdered, String ddpInstanceId,
                  String upsTrackingStatus, String upsTrackingDate, String upsReturnStatus, String upsReturnDate, UPSShipment shipment) {
        this.dsmKitRequestId = dsmKitRequestId;
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
        this.ddpInstanceId = ddpInstanceId;
        this.shipment = shipment;
    }


    public static boolean hasKitBeenOrderedInCE(Connection conn, String kitLabel) {
        String query = "select k.ce_order from ddp_kit k where k.kit_label = ?";
        boolean hasBeenOrdered = false;
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, kitLabel);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                hasBeenOrdered = rs.getBoolean(1);
                if (rs.next()) {
                    throw new RuntimeException("Too many rows found for kit " + kitLabel);
                }
            }
            else {
                throw new RuntimeException("Could not find kit " + kitLabel);
            }

        }
        catch (Exception e) {
            throw new RuntimeException("Could not determine ce_ordered status for " + kitLabel, e);
        }
        return hasBeenOrdered;
    }

    public static void updateCEOrdered(Connection conn, boolean ordered, String kitLabel) {
        String query = "UPDATE ddp_kit SET CE_order = ? where kit_label = ?";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setBoolean(1, ordered);
            stmt.setString(2, kitLabel);
            int r = stmt.executeUpdate();
            if (r != 1) {//number of subkits
                throw new RuntimeException("Update query for CE order flag updated " + r + " rows! with dsm kit " + kitLabel);
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Could not update ce_ordered status for " + kitLabel, e);
        }
    }


}

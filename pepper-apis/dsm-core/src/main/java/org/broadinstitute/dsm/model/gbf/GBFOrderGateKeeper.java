package org.broadinstitute.dsm.model.gbf;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Uses a {@link GBFOrderTransmitter} and {@link GBFOrderFinder}
 * to find and transmit orders to GBF for a given
 * DDP instance
 */
public class GBFOrderGateKeeper {

    private final GBFOrderFinder orderFinder;
    private final GBFOrderTransmitter transmitter;
    private final String ddpInstanceName;
    private static final String UPDATE_TRANSMISSION_DATE = "update ddp_kit_request req set req.transmitted_at = ? where external_order = ?";

    public GBFOrderGateKeeper(GBFOrderFinder orderFinder, GBFOrderTransmitter transmitter, String ddpInstanceName) {
        this.orderFinder = orderFinder;
        this.transmitter = transmitter;
        this.ddpInstanceName = ddpInstanceName;
    }


    public void sendPendingOrders(Connection conn) {
        for (SimpleKitOrder simpleKitOrder : orderFinder.findKitsToOrder(ddpInstanceName, conn)) {
            Response orderResponse = transmitter.orderKit(simpleKitOrder.getRecipientAddress(), simpleKitOrder.getExternalKitName(), simpleKitOrder.getExternalKitOrderNumber(), simpleKitOrder.getParticipantGuid());

            if (orderResponse.isSuccess()) {
                try (PreparedStatement stmt = conn.prepareStatement(UPDATE_TRANSMISSION_DATE)) {
                    stmt.setTimestamp(1, Timestamp.from(Instant.now()));
                    stmt.setString(2, simpleKitOrder.getExternalKitOrderNumber());
                    int numRowsUpdated = stmt.executeUpdate();

                    // putting 10 things in a kit seems crazy?
                    if (numRowsUpdated > 10) {
                        throw new RuntimeException("Updated " + numRowsUpdated + " for order " + simpleKitOrder.getExternalKitOrderNumber() + ".  That seems like a lot?");
                    }
                } catch (SQLException e) {
                    throw new RuntimeException("Order " + simpleKitOrder.getExternalKitOrderNumber() + " has been ordered, but we were unable to update the database.", e);
                }
            } else {
                throw new RuntimeException("Transmission of order " + simpleKitOrder.getExternalKitOrderNumber() + " failed with " + orderResponse.getErrorMessage());
            }
        }
    }

}
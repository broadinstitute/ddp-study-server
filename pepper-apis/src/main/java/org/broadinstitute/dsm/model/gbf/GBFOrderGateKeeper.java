package org.broadinstitute.dsm.model.gbf;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses a {@link GBFOrderTransmitter} and {@link GBFOrderFinder}
 * to find and transmit orders to GBF for a given
 * DDP instance
 */
public class GBFOrderGateKeeper {

    private static final Logger logger = LoggerFactory.getLogger(GBFOrderFinder.class);

    private final GBFOrderFinder orderFinder;
    private final GBFOrderTransmitter transmitter;
    private final String ddpInstanceName;
    private static final String UPDATE_TRANSMISSION_DATE = "update ddp_kit_request req set req.order_transmitted_at = ? where req.external_order_number = ?";

    public GBFOrderGateKeeper(GBFOrderFinder orderFinder, GBFOrderTransmitter transmitter, String ddpInstanceName) {
        this.orderFinder = orderFinder;
        this.transmitter = transmitter;
        this.ddpInstanceName = ddpInstanceName;
    }


    public void sendPendingOrders(Connection conn) {
        for (SimpleKitOrder simpleKitOrder : orderFinder.findKitsToOrder(ddpInstanceName, conn)) {
            Response orderResponse = transmitter.orderKit(simpleKitOrder.getRecipientAddress(), simpleKitOrder.getExternalKitName(), simpleKitOrder.getExternalKitOrderNumber(), simpleKitOrder.getParticipantGuid());

            if (orderResponse.isSuccess()) {
                // save result in separate transaction so that we minimize the chance
                // of large scale order duplication
                TransactionWrapper.inTransaction(markOrderAsTransmittedTxn -> {
                    try (PreparedStatement stmt = markOrderAsTransmittedTxn.prepareStatement(UPDATE_TRANSMISSION_DATE)) {
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
                    return  null;
                });

            } else {
                throw new RuntimeException("Transmission of order " + simpleKitOrder.getExternalKitOrderNumber() + " failed with " + orderResponse.getErrorMessage());
            }
        }
    }


    public static void main(String[] args) {
        RestHighLevelClient esClient = null;
        String dbUrl = args[0];
        String esUser = args[1];
        String esPassword = args[2];
        String esUrl = args[3];
        Config cfg = ConfigFactory.load();
        String gbfUrl = args[4];
        String apiKey = args[5];
        String carrierService = "3rd Day Air Residential";
        String externalClientId = args[6];
        TransactionWrapper.init(5, dbUrl,cfg, true);




        try {
            esClient = ElasticSearchUtil.getClientForElasticsearchCloud(esUrl, esUser, esPassword);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not initialize es client",e);
        }
        GBFOrderFinder orderFinder = new GBFOrderFinder(30, 10000, esClient, "participants_structured.testboston.testboston");

        GBFOrderTransmitter transmitter = new GBFOrderTransmitter(false, gbfUrl, apiKey, 2, 1000, carrierService,externalClientId);
        GBFOrderGateKeeper keeper = new GBFOrderGateKeeper(orderFinder, transmitter, "testboston");

        TransactionWrapper.inTransaction(conn -> {
            keeper.sendPendingOrders(conn);
            return null;
        });


        System.exit(0);
    }
}
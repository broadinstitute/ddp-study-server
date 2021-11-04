package org.broadinstitute.dsm.model.gbf;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds kit requests that should be sent to GBF.
 */
public class GBFOrderFinder {

    private static final Logger logger = LoggerFactory.getLogger(GBFOrderFinder.class);

    private final int maxOrdersToProcess;
    private final String esIndex;
    private final RestHighLevelClient esClient;
    private int maxDaysToReturnPreviousKit = 99999; // many years later

    private static final String FIND_KITS_TO_ORDER_QUERY =
            " " +
                    "select distinct " +
                    "subkit.external_name, " +
                    "orders.external_order_number, " +
                    "orders.ddp_participant_id, " +
                    "(select max(req.dsm_kit_request_id) from ddp_kit_request req where req.external_order_number = orders.external_order_number) as max_kit_request_id, " +
                    "(select req.order_transmitted_at from ddp_kit_request req where req.dsm_kit_request_id = orders.dsm_kit_request_id " +
                    "for update) as order_transmission_date " +
                    "from " +
                    "ddp_instance i, " +
                    "ddp_kit_request_settings s, " +
                    "sub_kits_settings subkit, " +
                    "(select distinct untransmitted.external_order_number, untransmitted.ddp_participant_id,  untransmitted.ddp_instance_id, " +
                    "      untransmitted.kit_type_id, untransmitted.dsm_kit_request_id " +
                    "      from " +
                    "      ddp_kit_request untransmitted, " +
                    "      ddp_instance i " +
                    "      where " +
                    "      i.instance_name = ? " +
                    "      and " +
                    "      i.ddp_instance_id = untransmitted.ddp_instance_id " +
                    "      and " +
                    "      untransmitted.order_transmitted_at is null " +
                    "      and " +
                    "      exists " +
                    "      (select delivered.external_order_number, delivered.ddp_participant_id,  delivered.ddp_instance_id, " +
                    "      delivered.kit_type_id, delivered.dsm_kit_request_id " +
                    "      from ddp_kit_request delivered, " +
                    "           ddp_kit k2, " +
                    "           ddp_instance i " +
                    "      where " +
                    "        delivered.ddp_participant_id = untransmitted.ddp_participant_id " +
                    "        and i.instance_name = ? " +
                    "        and untransmitted.dsm_kit_request_id != delivered.dsm_kit_request_id " +
                    "        and delivered.ddp_instance_id = i.ddp_instance_id " +
                    "        and k2.dsm_kit_request_id = delivered.dsm_kit_request_id " +
                    // any of: any kit for the ptp has been sent back, has a CE order or a result "
                    "        and (k2.CE_order is not null " +
                    "          or " +
                    "             k2.test_result is not null " +
                    "          or " +
                    "             k2.ups_return_status like 'D%' " +
                    "          or " +
                    "             k2.ups_return_status like 'I%' " +
                    "          ) " +
                    "        and ( " +
                    // it's been at most n days since the kit was delivered "
                    "          (k2.ups_tracking_status like 'D%' " +
                    "              and " +
                    "           DATE_ADD(str_to_date(k2.ups_tracking_date, '%Y%m%d %H%i%s'), INTERVAL ? DAY) > now()) " +
                    "          ) " +
                    "        and " +
                    "        delivered.order_transmitted_at is not null) " +
                    "        and not exists (select 1 from ddp_participant_exit e where e.ddp_instance_id = i.ddp_instance_id and e.ddp_participant_id = untransmitted.ddp_participant_id) " +
                    "      union " +
                    " " +
                    "      select distinct req.external_order_number, req.ddp_participant_id, req.ddp_instance_id, " +
                    "      req.kit_type_id, req.dsm_kit_request_id " +
                    "      from ddp_kit_request req, ddp_instance i " +
                    "      where  " +
                    "      i.instance_name = ? " +
                    "      and req.upload_reason is null " +
                    "      and req.order_transmitted_at is null " +
                    "      and req.ddp_instance_id = i.ddp_instance_id " +
                    "        and 1 = (select count(distinct req2.external_order_number) " +
                    "                 from ddp_kit_request req2 " +
                    "                 where req.ddp_participant_id = req2.ddp_participant_id " +
                    "                   and req.ddp_instance_id = req2.ddp_instance_id) " +
                    "                   and not exists (select 1 from ddp_participant_exit e where e.ddp_instance_id = i.ddp_instance_id and e.ddp_participant_id = req.ddp_participant_id) " +
                    "     ) as orders " +
                    "where " +
                    "i.instance_name = ? " +
                    "and " +
                    "i.ddp_instance_id = s.ddp_instance_id " +
                    "and " +
                    "subkit.ddp_kit_request_settings_id = s.ddp_kit_request_settings_id " +
                    "and " +
                    // todo arz do we have a better place for "GBF" constant?
                    "s.external_shipper = 'gbf' " +
                    "and " +
                    "subkit.kit_type_id = orders.kit_type_id " +
                    "order by max_kit_request_id asc limit ? ";

    public GBFOrderFinder(Integer maxDaysToReturnPreviousKit,
                          int maxOrdersToProcess,
                          RestHighLevelClient esClient,
                          String esIndex) {
        if (maxDaysToReturnPreviousKit != null) {
            this.maxDaysToReturnPreviousKit = maxDaysToReturnPreviousKit;
        }
        this.maxOrdersToProcess = maxOrdersToProcess;
        this.esClient = esClient;
        this.esIndex = esIndex;
    }

    public Collection<SimpleKitOrder> findKitsToOrder(String ddpInstanceName, Connection conn) {
        Set<String> participantGuids = new HashSet<>();
        List<SimpleKitOrder> kitsToOrder = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(FIND_KITS_TO_ORDER_QUERY,ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)) {
            stmt.setString(1, ddpInstanceName);
            stmt.setString(2, ddpInstanceName);
            stmt.setInt(3, maxDaysToReturnPreviousKit);
            stmt.setString(4, ddpInstanceName);
            stmt.setString(5, ddpInstanceName);
            stmt.setInt(6, maxOrdersToProcess);

            try (ResultSet rs = stmt.executeQuery()) {
                // loop through once to get participants
                while (rs.next()) {
                    participantGuids.add(rs.getString(DBConstants.DDP_PARTICIPANT_ID));
                }

                if (!participantGuids.isEmpty()) {
                    logger.info("Found {} participants", participantGuids.size());
                    Map<String, Address> addressForParticipants = ElasticSearchUtil.getParticipantAddresses(esClient, esIndex, participantGuids);
                    // now iterate again to get address
                    while (rs.previous()) {
                        String participantGuid = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String externalOrderId = rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER);
                        String kitName = rs.getString(DBConstants.EXTERNAL_KIT_NAME);
                        if (addressForParticipants.containsKey(participantGuid)) {
                            Address recipientAddress = addressForParticipants.get(participantGuid);
                            kitsToOrder.add(new SimpleKitOrder(recipientAddress, externalOrderId, kitName, participantGuid));
                        } else {
                            logger.error("No address found in elastic for {}",participantGuid);
                        }
                    }
                } // else no participants, which will happen if there are no pending kits
            }

        } catch (SQLException e) {
            throw new RuntimeException("Error querying kits", e);
        }
        return kitsToOrder;
    }

    /**
     * Prints out orders, but does not order them
     * @param args
     */
    public static void main(String[] args) {
        RestHighLevelClient esClient = null;
        String dbUrl = args[0];
        String esUser = args[1];
        String esPassword = args[2];
        String esUrl = args[3];
        int numDays = Integer.parseInt(args[4]);
        Config cfg = ConfigFactory.load();
        TransactionWrapper.init(1, dbUrl,cfg, true);


        try {
            esClient = ElasticSearchUtil.getClientForElasticsearchCloud(esUrl, esUser, esPassword);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not initialize es client",e);
        }
        GBFOrderFinder orderFinder = new GBFOrderFinder(numDays, 10000, esClient, "participants_structured.testboston.testboston");


        TransactionWrapper.inTransaction(conn -> {
            Collection<SimpleKitOrder> kits = orderFinder.findKitsToOrder("testboston", conn);
            StringBuilder guids = new StringBuilder();
            for (SimpleKitOrder kit : kits) {
                guids.append("'").append(kit.getParticipantGuid()).append("',\n");
            }

            logger.info("Found {} kits with {} day return window", kits.size(), numDays);
            logger.info(guids.toString());
            return null;
        });
        System.exit(0);

    }
}

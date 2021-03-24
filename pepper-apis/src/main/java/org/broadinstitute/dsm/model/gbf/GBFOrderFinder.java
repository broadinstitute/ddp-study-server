package org.broadinstitute.dsm.model.gbf;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static List<String> orderIds = Arrays.asList(new String[] {
            "SQQ4WP57BMPD78ABSAZX",
            "J3P5N6IV5EQR9HKQOCFL",
            "GXQBV9QVYSMP4GL2OB24",
            "JZP3J5E8NSW5IZKB54GT",
            "RM8DT9UD9XYSUKYUZV6O",
            "AAQ7Z9SJMAIV1EUOCJZM",
            "EY2PUX0HLCJBP1NWZW13",
            "L9YO61RHWNKBE5KLDTQE",
            "VP0SN3Z353N7BITM893Z",
            "49XA9G9VSZY1QQYLUXLW",
            "9I7DVYEREFS8LCOSQE5L",
            "ZDKV9OYNT6I618LYNHB0",
            "JUOT2ENQMVMOCDMHKCQR",
            "M96GSYL2SJOIGN0E5VUC",
            "WDDUKFX9GMRZVATX8YKX",
            "WTLZTVC9J2M49KVQJ2XE",
            "XQ2ZAGQLWDIO8QT8B3UB",
            "GU1G8KJJPF35IKTXRTDO",
            "S5ILAH1QQ1K36O3TY10M",
            "UAJTZCUPWOMWLRARJN1W",
            "YNJS83E58EKRH0YNY97Q",
            "SMBMGTIEFOEXHOWYMEOL",
            "S7FZ1K32P1IM73TPGWHH",
            "8V8UGNLKB5CIKSVIQ9KO",
            "JHUEOCOM28HR8UV0L2F2",
            "UXWHIZK8BN0PI1BXD56I",
            "25N8KZ8UKWHO28QI8730",
            "YJW9R5W3LP77Q2YYCTLQ",
            "LSEEHKWKQ9T16CJSOX68",
            "RTNWEC3RF3J498V38O1K",
            "D79UW9I2O76SQSU4AZJK",
            "SASYXGC3PXDSHCVW4VRA",
            "KP7A3WC37I7SF4ADPNZR",
            "5C8D74MBBFVT4SH71SU2",
            "ZPQZ3XEHN6D6R1E0KT8M",
            "9PXW3HB2QH3P4VUWYES9",
            "ZKHQ1SXFGSIZP1YXQSW1",
            "02I98RWRRZ5PBZTCXCD7",
            "WODTQ51DE0LQ88W0KRMF",
            "0HWG8I7OU54A8418PN61",
            "UXY2X8Y2E85UJH6XZKKL",
            "LEUQYSGS26NIX9UW85EJ",
            "3Q03BVADHHLZLCYLW5P2",
            "GFL3OIE0KYSLJUP0ES1C",
            "92G4EDCCFSGTGKURUA2W",
            "QALR7NOPEJFRW5ZUXBYH",
            "3DHBD4NLOPUX52V91DUS",
            "TTN4Z6D8U4ZVBIJD0HKP",
            "JF4TKO9ZWO8BJEOEFM5D",
            "X74JQ6QVGR3FSWHBQXS9",
            "B0O1QDNSHKHB6LTAM0BZ",
            "926GK1VO3J1QOH4MSZPU",
            "S0TODHQ56NX5HTF5VGE1",
            "VKIBMS9RPRP20MXV5S2H",
            "YYHL8L0BSVZ8BNVIAIXP",
            "5R9ATR3UT7GEMI2WRUVG",
            "G07YNC2D96ZNRYPFTNLW",
            "3PUGG4ZMD9TYGPJCGJ4K",
            "TYQIDJBX2J6NZI6LWPT5",
            "ZG69V6YZ13MJQK27W4UQ",
            "GW49RCF0U8LFENS13W0B",
            "V1YG1TB5VXJ1OLKOI50S",
            "2UC9QIBFU87K7M9WDAMZ",
            "L6GXQKEA3JTSCTF2LTB7",
            "BOQSY60ODF1O05OC082B",
            "62VC8Y8Y3PICTUA2F4F1",
            "QYLZH8426C162APOOUMW",
            "KJXZXARPM1FNB9U9PKBV",
            "6325U6ADP37G4T4BK8ET",
            "FOD8T93K7BZ3RELNZSJ2",
            "W5LKDPDZY7AOLLLVOQKB",
            "ZXQIJ3RUG8U9YMMQTMVQ",
            "YYEXXXJAHDUF6436EWHJ",
            "E68GDBP0VYYZPHOEH3PJ",
            "CG8XA5I4C4AKOOUIOVX2",
            "6BX54PUDVSQMGS9LUL4G",
            "93ICFQ8A5SFD1DPMJ6Z1",
            "4E7WEQDKRWXUG55905XK",
            "DO1UNDDYL2DFJ2ZDIY92",
            "I6JUWPO19IS8C2LXHWPG",
            "G70MT45UDDOKI1DARFC3",
            "SY06ET2G90BI3FPPDNI9",
            "P718OZKR8U7KTH6N8KFI",
            "NR74DMEZM6QFBF8ZP2P3",
            "MTFA8SR5PBTP70GG8ADR",
            "34M69265SHC3QY3GOCHT",
            "CJ2ABXDD9II8IM0V5QMF",
            "F5XU3IZ38OA22UY2CHOX",
            "HV5ZZ2O8UOCMH4JFAHWY",
            "71K92OVQ9ZS0WUQ874MX",
            "IVBMS6V1I34J0MVQUX24",
            "GE1ROXROHGGK3PWDRXH8",
            "2UUY4FTK8KSGLLAQSTYY",
            "QRW4KVGELM11Y5WXGHIS",
            "2OJT1HCCGMA579DNRPLC",
            "G17R1UXRDRENNELIF703",
            "SN8PQ2J0J9YBYFLASC13",
            "KY1P63WYR00K3620L9A3",
            "5S4N6G5R36HWGER6JKUR",
            "P0G2O5SAK3ENAY78MCKB",
            "MPPGNQSLD92AB821LVXM",
            "6CFQGXTG45RIAW0CDICH",
            "DS1LQ0S8XED0ZPU1XEVR",
            "YICN03EMHQJ800BQO5CU",
            "MPI0YCI62YEOHUXRWFMP",
            "SZOEIEXWRTR3F0EE0RMI",
            "78ZGTMFF2IMSDVCF1BPP",
            "J5PWR975XBP3FV27GM3I",
            "H1SVS7VN2YSM9RIGPG9A",
            "M3VH0DFHM9RAF432YSTO",
            "MOYT8NA9CFHZ5Z3ZK7G6",
            "GRNXGO76B0Z5RDIG5Z9D",
            "HMXVTAKHCCUCJPAM8PP8",
            "I3IK8KH44L5JVK35GV8M",
            "E29Z8HXQ2XNPVUEGRXSU",
            "AQQSTDQZ8C8UEOMNLSNQ",
            "8KATB9CH4WWBF79CXFE2",
            "BGNG4ORR1L29AGG2C853",
            "W4HSNM0NAW3VAK6SIBVO",
            "5K00USPWBG4PUWCLNE4D",
            "JATRUETQI7SZYG5D70Z5",
            "L7DPPYLKRZP2WWRA0MRP",
            "MA5P6EV1QJ3EQBDMJXDJ",
            "O42IKUASWN4DN9VZDQAZ",
            "PY6MEAHSX0K4DIZLYC3P",
            "1TCKN2ZSB5BTB5HKSRC5",
            "VMJGKA5WDDCW53YP52LY",
            "Y7HQ02QDLMOGF82HXXCT",
            "6MWTAH52DUDYTV36MUNE",
            "CLSD3LGU0BKJ9EN7XLKX",
            "GJKCEJINCXV123EVEQJ4",
            "KA3F0F6ONCJBRA07JTI7",
            "UFG9WQX9RSOYVF4MC08U",
            "Y2W2X995H7X3DYU3MZWJ",
            "008AZ023LPLL6FN07VO6",
            "LVIT1UVWXJKTINX968P8",
            "QF2IMDO08AL6D1XYAJ57",
            "RQ0Y0E17EBNYD7QQHGLK",
            "V3SZUA0ZC3PGZQWUUGDI"
    });

    private static final String FIND_KITS_TO_ORDER_QUERY =
            " " +
                    "select distinct " +
                    "subkit.external_name, " +
                    "orders.external_order_number, " +
                    "orders.ddp_participant_id, " +
                    "(select max(req.dsm_kit_request_id) from ddp_kit_request req where req.external_order_number = orders.external_order_number) as max_kit_request_id, " +
                    "(select req.order_transmitted_at from ddp_kit_request req where req.dsm_kit_request_id = orders.external_order_number " +
                    ") as order_transmission_date, " +
                    // todo arz enable view from dsm -> dss in all envs
                    "(select u.hruid from dss_user u where u.guid = orders.ddp_participant_id) as hruid " +
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
                    Map<String, Address> addressForParticipants = ElasticSearchUtil.getParticipantAddresses(esClient, esIndex, participantGuids);
                    // now iterate again to get address
                    while (rs.previous()) {
                        String participantGuid = rs.getString(DBConstants.DDP_PARTICIPANT_ID);
                        String externalOrderId = rs.getString(DBConstants.EXTERNAL_ORDER_NUMBER);
                        String kitName = rs.getString(DBConstants.EXTERNAL_KIT_NAME);
                        String shortId = rs.getString("hruid");
                        if (addressForParticipants.containsKey(participantGuid)) {
                            Address recipientAddress = addressForParticipants.get(participantGuid);

                            //if (orderIds.contains(externalOrderId)) {
                                kitsToOrder.add(new SimpleKitOrder(recipientAddress, externalOrderId, kitName, participantGuid, shortId));
                            //}
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
        Config cfg = ConfigFactory.load();
        TransactionWrapper.init(1, dbUrl,cfg, true);


        try {
            esClient = ElasticSearchUtil.getClientForElasticsearchCloud(esUrl, esUser, esPassword);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not initialize es client",e);
        }
        GBFOrderFinder orderFinder = new GBFOrderFinder(30, 10000, esClient, "participants_structured.testboston.testboston");


        TransactionWrapper.inTransaction(conn -> {
            StringBuilder csvString = new StringBuilder();
            csvString.append("short id,kit order number\n");
            Collection<SimpleKitOrder> kits = orderFinder.findKitsToOrder("testboston", conn);
            logger.info("Found {} kits.", kits.size());
            for (SimpleKitOrder kit : kits) {
                logger.info("Found {}",kit.getExternalKitOrderNumber());
                csvString.append(kit.getShortId()).append(",").append(kit.getExternalKitOrderNumber()).append("\n");
            }
            logger.info(csvString.toString());
            return null;
        });
        System.exit(0);

    }
}

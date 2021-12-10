package org.broadinstitute.dsm.cf;

import com.google.cloud.functions.BackgroundFunction;
import com.google.cloud.functions.Context;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.typesafe.config.Config;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.broadinstitute.dsm.careevolve.*;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.DdpKit;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;

import java.sql.Connection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.broadinstitute.dsm.statics.ApplicationConfigConstants.DSM_DB_URL;

public class Covid19OrderRegistrarFunction  implements BackgroundFunction<Covid19OrderRegistrarFunction.OrderPayload> {

    private static final Logger logger = Logger.getLogger(TestBostonKitTrackerDispatcher.class.getName());

    @Override
    public void accept(OrderPayload orderPayload, Context context) throws Exception {
        logger.info("Processing request " + orderPayload);
        // check first to see if it's already ordered.

        Config cfg = CFUtil.loadConfig();
        String careEvolveAccount = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ACCOUNT);
        String careEvolveSubscriberKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SUBSCRIBER_KEY);
        String careEvolveServiceKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SERVICE_KEY);
        String careEvolveOrderEndpoint = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ORDER_ENDPOINT);
        Authentication auth = new Authentication(careEvolveSubscriberKey, careEvolveServiceKey);
        Provider provider = new Provider(cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_FIRSTNAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_LAST_NAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_NPI));

        PoolingDataSource<PoolableConnection> dataSource = CFUtil.createDataSource(5, cfg.getString(DSM_DB_URL));

        DDPInstance ddpInstance = null;

        try (Connection conn = dataSource.getConnection()) {
            ddpInstance = DDPInstance.getDDPInstanceWithRole("testboston", DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
            logger.info("Will use instance " + ddpInstance.getName());

            if (DdpKit.hasKitBeenOrderedInCE(conn, orderPayload.getKitLabel())) {
                logger.log(Level.WARNING, orderPayload.getKitLabel() + " has already been ordered in CE");
                return;
            }
        }

        String esUrl = cfg.getString(ApplicationConfigConstants.ES_URL);
        String esUsername = cfg.getString(ApplicationConfigConstants.ES_USERNAME);
        String esPassword = cfg.getString(ApplicationConfigConstants.ES_PASSWORD);
        String esProxy = cfg.getString(ApplicationConfigConstants.ES_PROXY);
        RestHighLevelClient esClient = ElasticSearchUtil.getClientForElasticsearchCloud(esUrl, esUsername, esPassword, esProxy);

        Covid19OrderRegistrar orderRegistrar = new Covid19OrderRegistrar(careEvolveOrderEndpoint, careEvolveAccount, provider, 0, 0);

        Map<String, Map<String, Object>> esData = ElasticSearchUtil.getSingleParticipantFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES(), esClient, orderPayload.getParticipantHruid());

        if (esData.size() == 1) {
            JsonObject participantJsonData = new JsonParser().parse(new Gson().toJson(esData.values().iterator().next())).getAsJsonObject();
            Patient cePatient = Covid19OrderRegistrar.fromElasticData(participantJsonData);
            System.out.println(cePatient);

            OrderResponse orderResponse = orderRegistrar.orderTest(auth, cePatient, orderPayload.getKitLabel(), orderPayload.getExternalOrderId(), orderPayload.getCollectionTime());

            if (orderResponse.hasError()) {
                logger.log(Level.SEVERE, "Trouble placing order for " + orderPayload.getKitLabel() + ":" + orderResponse.getError());
            } else {
                logger.info(orderPayload.getKitLabel() + " has been placed for " + orderPayload.getParticipantHruid() + ".  CE id is " + orderResponse.getHandle());
                try (Connection conn = dataSource.getConnection()) {
                    DdpKit.updateCEOrdered(conn, true, orderPayload.getKitLabel());
                }
            }
        } else {
            throw new RuntimeException("Could not find es data for " + orderPayload.getParticipantHruid());
        }
    }

    public static class OrderPayload extends BaseCloudFunctionPayload {

        @SerializedName("hruid")
        private String participantHruid;

        // yyyy-MM-dd hh:mm, US/NY timezone
        @SerializedName("collectionTime")
        private String collectionTime;

        @SerializedName("kitLabel")
        private String kitLabel;

        @SerializedName("externalOrderId")
        private String externalOrderId;

        public String getParticipantHruid() {
            return participantHruid;
        }

        public String getRawCollectionTime() {
            return collectionTime;
        }

        public String getKitLabel() {
            return kitLabel;
        }

        public String getExternalOrderId() {
            return externalOrderId;
        }

        public Instant getCollectionTime() {
            Instant collectionInstant = null;
            try {
                var dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm");
                dateFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
                collectionInstant =  dateFormat.parse(collectionTime).toInstant();
            } catch(ParseException e) {
                throw new RuntimeException("Could not parse " + collectionTime + " for " + kitLabel, e);
            }
            return collectionInstant;
        }

        @Override
        public String toString() {
            return "OrderPayload{" +
                    "participantHruid='" + participantHruid + '\'' +
                    ", collectionTime='" + collectionTime + '\'' +
                    ", kitLabel='" + kitLabel + '\'' +
                    ", externalOrderId='" + externalOrderId + '\'' +
                    '}';
        }
    }
}

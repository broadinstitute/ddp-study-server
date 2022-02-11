package org.broadinstitute.dsm.careevolve;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.cf.CFUtil;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.DdpKit;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.ElasticSearchUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Map;


public class Covid19OrderRegistrarTest {

    private static final Logger logger = LoggerFactory.getLogger(Covid19OrderRegistrarTest.class);

    private static Authentication auth;

    private static String careEvolveOrderEndpoint;

    private static String  careEvolveAccount;

    private static Config cfg;

    private static Provider provider;

    @BeforeClass
    public static void beforeClass() throws Exception {
        cfg = ConfigFactory.load();
        TransactionWrapper.init(20, cfg.getString("portal.dbUrl"), cfg, true);

        // todo pull this out to a file, refresh from secret manager

    }

    @Ignore
    @Test
    public void testOrderForParticipant() throws Exception {
        PoolingDataSource<PoolableConnection> dataSource = CFUtil.createDataSource(5, cfg.getString("portal.dbUrl"));

        careEvolveAccount = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ACCOUNT);
        String careEvolveSubscriberKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SUBSCRIBER_KEY);
        String careEvolveServiceKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SERVICE_KEY);
        careEvolveOrderEndpoint = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ORDER_ENDPOINT);
        auth = new Authentication(careEvolveSubscriberKey, careEvolveServiceKey);
        provider = new Provider(cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_FIRSTNAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_LAST_NAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_NPI));



        DDPInstance ddpInstance = null;

        try (Connection conn = dataSource.getConnection()) {
            ddpInstance = DDPInstance.getDDPInstanceWithRole("testboston", DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
        }

        String participantHruid = "";
        String kitLabel = "";
        String externalOrderNumber = "";
        String collectionTime = "";
        String esUrl = cfg.getString(ApplicationConfigConstants.ES_URL);
        String esUsername = cfg.getString(ApplicationConfigConstants.ES_USERNAME);
        String esPassword = cfg.getString(ApplicationConfigConstants.ES_PASSWORD);
        RestHighLevelClient esClient = ElasticSearchUtil.getClientForElasticsearchCloud(esUrl, esUsername, esPassword);

        Covid19OrderRegistrar orderRegistrar = new Covid19OrderRegistrar(careEvolveOrderEndpoint, careEvolveAccount, provider, 0, 0);

        Map<String, Map<String, Object>> esData = ElasticSearchUtil.getSingleParticipantFromES(ddpInstance.getName(), ddpInstance.getParticipantIndexES(), esClient, participantHruid);

        if (esData.size() == 1) {
            JsonObject participantJsonData = new JsonParser().parse(new Gson().toJson(esData.values().iterator().next())).getAsJsonObject();
            Patient cePatient = Covid19OrderRegistrar.fromElasticData(participantJsonData);
            System.out.println(cePatient);

            Instant collectionDate = new SimpleDateFormat("MM/dd/yyyy hh:mm").parse(collectionTime).toInstant();
            orderRegistrar.orderTest(auth,cePatient, kitLabel, externalOrderNumber, collectionDate);
            try (Connection conn = dataSource.getConnection()) {
                DdpKit.updateCEOrdered(dataSource.getConnection(), true, kitLabel);
                conn.commit();
            }
        } else {
            throw new RuntimeException("Could not find es data for " + participantHruid);
        }
    }
}

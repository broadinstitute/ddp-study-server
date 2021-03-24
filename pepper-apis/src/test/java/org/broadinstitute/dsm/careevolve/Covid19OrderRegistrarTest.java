package org.broadinstitute.dsm.careevolve;

import java.io.File;
import java.time.Instant;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/test-config.conf")));
        TransactionWrapper.init(20, cfg.getString("portal.dbUrl"), cfg, true);
        //startMockServer();
        //setupUtils();

        cfg = ConfigFactory.load().withFallback(ConfigFactory.parseFile(new File("config/ellkay.conf")));
        careEvolveAccount = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ACCOUNT);
        String careEvolveSubscriberKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SUBSCRIBER_KEY);
        String careEvolveServiceKey = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_SERVICE_KEY);
        careEvolveOrderEndpoint = cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_ORDER_ENDPOINT);
        auth = new Authentication(careEvolveSubscriberKey, careEvolveServiceKey);
        provider = new Provider(cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_FIRSTNAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_LAST_NAME),
                cfg.getString(ApplicationConfigConstants.CARE_EVOLVE_PROVIDER_NPI));
    }

    @Ignore
    @Test
    public void testOrderForParticipant() throws Exception {
        Covid19OrderRegistrar orderRegistrar = new Covid19OrderRegistrar(careEvolveOrderEndpoint, careEvolveAccount, provider, 0, 0);

        orderRegistrar.orderTest(auth,"PUTPKX","TBOS-112211221","kit130", Instant.now());

    }
}

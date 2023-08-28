package org.broadinstitute.dsm.mercury;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.lddp.security.SecurityHelper;
import org.junit.Test;

public class MercuryEndpointTests {

    public static final long ONE_DAY_IN_SECONDS = 24 * 60 * 60;

    public static long getCurrentUnixUTCTime() {
        return System.currentTimeMillis() / 1000L;
    }

    @Test
    public void testCreationAPI() {
        Config cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File("config/test-config.conf")));
        String bspSecret = cfg.getString("bsp.secret");
        String bspToken = SecurityHelper.createTokenWithSigner(bspSecret, getCurrentUnixUTCTime() + ONE_DAY_IN_SECONDS,
                new HashMap<>(), SecurityHelper.BSP_SIGNER);

        String participantId = "BrainProject_P9DH6G";
        String type = "ffpe-section";
        String barcode = "123Test";
        String command =
                "curl -i  -H \"Authorization: Bearer %s\" https://dsm-dev.datadonationplatform.org/ddp/createClinicalDummy/%s/%s/%s";
        command = String.format(command, bspToken, barcode, type, participantId);
        //        try {
        //            Process process = Runtime.getRuntime().exec(command);
        //        } catch (IOException e) {
        //            e.printStackTrace();
        //        }

        String accessionCommand = "curl -i -H \"Authorization: Bearer %s\" https://dsm-dev.datadonationplatform.org/ddp/ClinicalKits/%s";

        accessionCommand = String.format(accessionCommand, bspToken, barcode);
        try {
            Process process = Runtime.getRuntime().exec(accessionCommand);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

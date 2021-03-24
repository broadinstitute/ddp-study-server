package org.broadinstitute.dsm;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.exception.FileColumnMissing;
import org.broadinstitute.dsm.route.KitUploadRoute;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.TestUtil;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;

public class KitUploadRouteTest {

    private static Config cfg;

    private static KitUploadRoute route;

    @BeforeClass
    public static void doFirst() throws Exception {
        cfg = ConfigFactory.load();
        //secrets from vault in a config file
        cfg = cfg.withFallback(ConfigFactory.parseFile(new File(System.getenv("TEST_CONFIG_FILE"))));
        cfg = cfg.withValue("errorAlert.recipientAddress", ConfigValueFactory.fromAnyRef(""));

        if (!cfg.getString("portal.environment").startsWith("Local")) {
            throw new RuntimeException("Not local environment");
        }

        if (!cfg.getString("portal.dbUrl").contains("local")) {
            throw new RuntimeException("Not your test db");
        }

        TransactionWrapper.configureSslProperties(cfg.getString("portal.dbSslKeyStore"),
                cfg.getString("portal.dbSslKeyStorePwd"),
                cfg.getString("portal.dbSslTrustStore"),
                cfg.getString("portal.dbSslTrustStorePwd"));
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
        TransactionWrapper.init(cfg.getInt("portal.maxConnections"), cfg.getString("portal.dbUrl"), cfg, false);

        NotificationUtil notificationUtil = new NotificationUtil(cfg);
        route = new KitUploadRoute(notificationUtil);
    }

    @Test
    public void headerWithSignatureAndWithoutShortId() {
        String fileContent = "participantId\tsignature\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tSun Maid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        Assert.assertTrue(route.isFileValid(fileContent).size() > 0);
    }

    @Test
    public void headerWithoutSignatureAndWithShortId() {
        String fileContent = "participantId\tshortId\tfirstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\t56\tSun\tMaid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        Assert.assertTrue(route.isFileValid(fileContent).size() > 0);
    }

    @Test
    public void headerMissingShortId() {
        String fileContent = "participantId\tfirstName\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tSun\tMaid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent);
        }
        catch (FileColumnMissing e) {
            Assert.assertTrue(e.getMessage().endsWith("shortId"));
        }
    }

    @Test
    public void headerMissingShortIdHavingSignature() {
        String fileContent = "participantId\tsignature\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\tSun Maid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent);
        }
        catch (FileColumnMissing e) {
            Assert.assertTrue(e.getMessage().endsWith("shortId"));
        }
    }

    @Test
    public void headerMissingFirstNameWithShortId() {
        String fileContent = "participantId\tshortId\tlastName\tstreet1\tstreet2\tcity\tstate\tpostalCode\tcountry\n" +
                "56\t56\tMaid\t415 Main St\t\tCambridge\tMA\t2142\tUS";
        try {
            route.isFileValid(fileContent);
        }
        catch (FileColumnMissing e) {
            Assert.assertTrue(e.getMessage().endsWith("firstName or signature"));
        }
    }
}

package org.broadinstitute.dsm;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.LatestKitRequest;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DBTestUtil;
import org.broadinstitute.dsm.util.tools.KitRequestMigrationTool;
import org.broadinstitute.dsm.util.TestUtil;
import org.broadinstitute.dsm.util.tools.util.DBUtil;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class KitRequestMigrationToolTest extends TestHelper {

    private static String lastKitBeforeMigration;

    @BeforeClass
    public static void first() {
        setupDB();

        List<String> strings = new ArrayList<>();
        strings.add(DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
        strings.add(DBConstants.PDF_DOWNLOAD_CONSENT);
        strings.add(DBConstants.PDF_DOWNLOAD_RELEASE);
        strings.add(TEST_DDP);

        lastKitBeforeMigration = DBTestUtil.getStringFromQuery(LatestKitRequest.SQL_SELECT_LATEST_KIT_REQUESTS + " and site.instance_name = ?", strings, "last_kit");

        TransactionWrapper.reset(TestUtil.UNIT_TEST);
    }

    @Test
    public void testMigrationTool() {
        KitRequestMigrationTool.argumentsForTesting("config/test-config.conf", TEST_DDP, "BLOOD",
                "KitRequestMigration_kits.txt", "txt");
//        KitRequestMigrationTool.argumentsForTesting("config/test-config.conf", MBC, "SALIVA",
//                "MBC_Salivakits_06212017.txt", "txt");
        KitRequestMigrationTool.littleMain();
        List<String> strings = new ArrayList<>();
        strings.add(DBConstants.HAS_KIT_REQUEST_ENDPOINTS);
        strings.add(DBConstants.PDF_DOWNLOAD_CONSENT);
        strings.add(DBConstants.PDF_DOWNLOAD_RELEASE);
        strings.add(TEST_DDP);
        String lastKitAfterMigration = DBTestUtil.getStringFromQuery(LatestKitRequest.SQL_SELECT_LATEST_KIT_REQUESTS + " and site.instance_name = ?", strings, "last_kit");
        //check that latest kit doesn't start with "MIGRATED"
        Assert.assertEquals(lastKitBeforeMigration, lastKitAfterMigration);
        if (StringUtils.isNotBlank(lastKitAfterMigration)) {
            Assert.assertTrue(!lastKitAfterMigration.startsWith("MIGRATED"));
        }
    }

    @Test
    public void longFromShortDateString() {
        String dateString = "10/20/16";
        Long dateLong = 1476936000*1000L;
        Assert.assertEquals(dateLong, DBUtil.getLong(dateString));
    }

    @Test
    public void longFromDateString() {
        String dateString = "10/20/2016";
        Long dateLong = 1476936000*1000L;
        Assert.assertEquals(dateLong, DBUtil.getLong(dateString));
    }

    @AfterClass
    public static void stopMockServer() {
        TransactionWrapper.reset(TestUtil.UNIT_TEST);

        TransactionWrapper.init(cfg.getInt(ApplicationConfigConstants.DSM_DB_MAX_CONNECTIONS),
                cfg.getString(ApplicationConfigConstants.DSM_DB_URL), cfg, false);
        //delete all KitRequests added by the test
        DBTestUtil.deleteAllKitData("66666");
        DBTestUtil.deleteAllKitData("66667");
        DBTestUtil.deleteAllKitData("66668");
        DBTestUtil.deleteAllKitData("66669");
        DBTestUtil.deleteAllKitData("66670");
        DBTestUtil.deleteAllKitData("66671");
        DBTestUtil.executeQuery("UPDATE ddp_instance set is_active = 0 where instance_name = \"" + TEST_DDP + "\"");
        TransactionWrapper.reset(TestUtil.UNIT_TEST);
    }
}

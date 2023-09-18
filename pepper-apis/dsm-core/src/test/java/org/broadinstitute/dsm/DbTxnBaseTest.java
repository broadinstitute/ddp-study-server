package org.broadinstitute.dsm;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.DatabaseTestUtil;
import org.broadinstitute.dsm.util.DSMDbTestContainer;
import org.broadinstitute.dsm.util.DbSharedTestContent;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class DbTxnBaseTest extends ElasticBaseTest {
    @BeforeClass
    public static void initDbConnection() {
        DSMDbTestContainer.initializeTestDbs();
        DatabaseTestUtil.initDbConnection(ConfigManager.getInstance().getConfig(), TransactionWrapper.DB.DSM);
        DbSharedTestContent.createContent();
    }

    @AfterClass
    public static void resetDbConnection() {
        DatabaseTestUtil.closeDbConnection();
    }
}

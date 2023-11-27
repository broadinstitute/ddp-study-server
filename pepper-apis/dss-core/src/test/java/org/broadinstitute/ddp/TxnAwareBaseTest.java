package org.broadinstitute.ddp;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.DBTestContainer;
import org.broadinstitute.ddp.util.DatabaseTestUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 * Base class to setup config and database connection for running
 * tests that need transactions only for the pepper APIs database.
 */
@Slf4j
public abstract class TxnAwareBaseTest extends ConfigAwareBaseTest {
    @BeforeClass
    public static void initDbConnection() {
        DBTestContainer.initializeTestDbs();
        DatabaseTestUtil.initDbConnection(ConfigManager.getInstance().getConfig(), TransactionWrapper.DB.APIS);
        DBUtils.loadDaoSqlCommands(sqlConfig);
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
    }

    @AfterClass
    public static void resetDbConnection() {
        DatabaseTestUtil.closeDbConnection();
    }
}

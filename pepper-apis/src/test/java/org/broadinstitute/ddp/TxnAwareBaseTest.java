package org.broadinstitute.ddp;

import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class to setup config and database connection for running
 * tests that need transactions only for the pepper APIs database.
 */
public abstract class TxnAwareBaseTest extends ConfigAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(TxnAwareBaseTest.class);

    @BeforeClass
    public static void initDbConnection() {
        MySqlTestContainerUtil.initializeTestDbs();
        cfg = ConfigManager.getInstance().getConfig();

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
        LOG.info("Initializing db pool for " + dbUrl);
        TransactionWrapper.reset();
        TransactionWrapper.init(new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.APIS, maxConnections, dbUrl));

        LiquibaseUtil.runLiquibase(dbUrl, TransactionWrapper.DB.APIS);
        DBUtils.loadDaoSqlCommands(sqlConfig);
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, LanguageStore::init);
    }

    @AfterClass
    public static void resetDbConnection() {
        TransactionWrapper.reset();
    }
}

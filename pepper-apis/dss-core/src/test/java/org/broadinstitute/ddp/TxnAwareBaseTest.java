package org.broadinstitute.ddp;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.broadinstitute.ddp.util.MySqlTestContainerUtil;
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
        MySqlTestContainerUtil.initializeTestDbs();
        cfg = ConfigManager.getInstance().getConfig();

        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
        log.info("Initializing db pool");
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

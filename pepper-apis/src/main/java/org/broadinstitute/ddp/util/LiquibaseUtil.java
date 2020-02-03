package org.broadinstitute.ddp.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.SQLException;
import java.time.Instant;

import com.typesafe.config.Config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.exception.MigrationFailedException;
import liquibase.exception.RollbackFailedException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.resource.FileSystemResourceAccessor;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LiquibaseUtil implements  AutoCloseable {

    public static final String PEPPER_APIS_GLOBAL_MIGRATIONS = "src/main/resources/changelog-master.xml";

    public static final String HOUSEKEEPING_GLOBAL_MIGRATIONS = "src/main/resources/housekeeping-changelog-master.xml";

    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseUtil.class);

    public static final String AUTH0_TENANT_MIGRATION = "src/main/resources/db-changes/tenant-migration.xml";

    private HikariDataSource dataSource;

    private LiquibaseUtil(String dbUrl)  {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        dataSource = new HikariDataSource(config);
    }

    @Override
    public void close()  {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    /**
     * Runs the global liquibase migrations against the given database url using a connection that's auto-closed.
     */
    public static void runLiquibase(String dbUrl, DB db) {
        try {
            try (LiquibaseUtil liquibaseUtil = new LiquibaseUtil(dbUrl)) {

                if (db == DB.APIS) {
                    liquibaseUtil.runPepperAPIsGlobalMigrations();
                } else if (db == DB.HOUSEKEEPING) {
                    liquibaseUtil.runHousekeepingGlobalMigrations();
                } else {
                    throw new DDPException("Unknown database: " + db.name());
                }
            }
        } catch (Exception e) {
            throw new DDPException("Error running liquibase migrations", e);
        }
    }

    /**
     * Runs migrations using given changelog file and a connection that's auto-closed. This is useful for test classes
     * that want to load their own custom data for a test.
     */
    public static void runChangeLog(String dbUrl, String changeLogFile) {
        try {
            try (LiquibaseUtil util = new LiquibaseUtil(dbUrl)) {
                util.runMigrations(changeLogFile);
            }
        } catch (Exception e) {
            throw new DDPException("Error running liquibase migrations for changeLogFile " + changeLogFile, e);
        }
    }

    /**
     * Runs the global migration scripts.  These are the scripts from {@link #PEPPER_APIS_GLOBAL_MIGRATIONS here}
     * which will be applied to test databases as well as production.
     */
    private void runPepperAPIsGlobalMigrations() throws LiquibaseException, SQLException {
        runMigrations(PEPPER_APIS_GLOBAL_MIGRATIONS);
        insertLegacyTenant();
        // run script to update client -> tenant and study -> tenant and enable constraints
        runMigrations(AUTH0_TENANT_MIGRATION);
    }

    /**
     * Runs the global migration scripts.  These are the scripts from {@link #PEPPER_APIS_GLOBAL_MIGRATIONS here}
     * which will be applied to test databases as well as production.
     *
     * @throws LiquibaseException if something goes wrong
     */
    private void runHousekeepingGlobalMigrations() throws LiquibaseException, SQLException {
        runMigrations(HOUSEKEEPING_GLOBAL_MIGRATIONS);
    }

    /**
     * Runs the specific changelog file. When migration fails, this will attempt to rollback the changes, as applicable.
     */
    private void runMigrations(String changelogFile) throws LiquibaseException, SQLException {
        Liquibase liquibase = null;
        String tag = null;
        try {
            liquibase = new Liquibase(changelogFile, new FileSystemResourceAccessor(), new JdbcConnection(dataSource.getConnection()));
            logLocks(liquibase.listLocks());

            tag = generateDatabaseTag();
            liquibase.tag(tag);
            LOG.info("Tagged database with tag {}", tag);

            liquibase.update(new Contexts());
        } catch (MigrationFailedException originalError) {
            if (liquibase != null && tag != null) {
                try {
                    LOG.info("Attempting to rollback changesets to tag {}", tag);
                    liquibase.rollback(tag, new Contexts());
                    LOG.info("Successfully rolled back changesets to tag {}", tag);
                } catch (RollbackFailedException e) {
                    LOG.error("Failed to rollback changesets to tag {}, database might be in a bad state", tag, e);
                }
            } else {
                LOG.error("No liquibase object or tag to rollback changesets");
            }

            // Propagate original exception back up.
            throw originalError;
        } finally {
            if (liquibase != null) {
                liquibase.forceReleaseLocks();
            }
        }
    }

    // todo arz make this work with existing local test script and real deployment
    // do migration in both test and non-test script, with conditional protection

    /**
     * Inserts the environment-sensitive tenant used for testing.
     * Returns true if tenant was inserted.
     */
    private static boolean insertLegacyTenant() {
        Config cfg = ConfigManager.getInstance().getConfig();
        boolean insertedTenant = false;
        boolean resetDb = false;

        String pepperApisDbUrl = cfg.getString(DB.APIS.getDbUrlConfigKey());
        String defaultTimeZoneName = cfg.getString(ConfigFile.DEFAULT_TIMEZONE);

        if (!TransactionWrapper.isInitialized()) {
            TransactionWrapper.init(defaultTimeZoneName, new TransactionWrapper.DbConfiguration(DB.APIS, 1, pepperApisDbUrl));
            resetDb = true;
        }
        // insert legacy auth0 tenant data
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        insertedTenant = TransactionWrapper.withTxn(DB.APIS, handle -> {
            return insertLegacyTenant(handle, auth0Config);
        });
        if (resetDb) {
            TransactionWrapper.reset();
        }
        return insertedTenant;
    }

    /**
     * Inserts the environment-sensitive tenant used for testing
     * via the given handle and config.  Returns true if tenant
     * was inserted, false otherwise.
     */
    private static boolean insertLegacyTenant(Handle handle, Config auth0Cfg) {
        JdbiAuth0Tenant jdbiAuth0Tenant = handle.attach(JdbiAuth0Tenant.class);
        boolean insertedTenant = false;
        if (auth0Cfg.hasPath(ConfigFile.DOMAIN) && auth0Cfg.hasPath(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID)
                && auth0Cfg.hasPath(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET)) {
            String domain = auth0Cfg.getString(ConfigFile.DOMAIN);
            String mgmtApiClient = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_ID);
            String mgmtSecret = auth0Cfg.getString(ConfigFile.Auth0Testing.AUTH0_MGMT_API_CLIENT_SECRET);

            String encryptedSecret = AesUtil.encrypt(mgmtSecret, EncryptionKey.getEncryptionKey());
            Auth0TenantDto tenantDto = jdbiAuth0Tenant.insertIfNotExists(domain, mgmtApiClient, encryptedSecret);
            LOG.info("Inserted testing tenant {}", domain);
            insertedTenant = true;
        } else {
            LOG.info("No legacy domain/mgt secret in config, skipping insert of auth0_tenant data");
        }
        return insertedTenant;
    }


    /**
     * Writes logging information about the locks.
     */
    private void logLocks(DatabaseChangeLogLock[] databaseChangeLogLocks) {
        boolean hasLocks = false;
        if (databaseChangeLogLocks != null) {
            if (databaseChangeLogLocks.length > 0) {
                hasLocks = true;
                for (DatabaseChangeLogLock dbLock : databaseChangeLogLocks) {
                    LOG.info("Liquibase locked by {} at {}.  Lock id is {} ", dbLock.getLockedBy(),
                            dbLock.getLockGranted(), dbLock.getId());
                }
            }
        }
        if (!hasLocks) {
            LOG.info("No liquibase locks");
        }
    }

    /**
     * Get a tag that serves as timestamp of last-known-good database before running migrations, and serves as a point
     * of reference for rolling back bad migrations.
     */
    private String generateDatabaseTag() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            hostname = RouteConstants.API.VERSION;
            LOG.warn("Unable to get hostname to create tag, defaulting to {}", hostname, e);
        }
        return String.format("%d-%s", Instant.now().toEpochMilli(), hostname);
    }
}

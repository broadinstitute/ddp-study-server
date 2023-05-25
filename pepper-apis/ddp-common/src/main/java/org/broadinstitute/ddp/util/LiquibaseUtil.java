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
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.exception.MigrationFailedException;
import liquibase.exception.RollbackFailedException;
import liquibase.lockservice.DatabaseChangeLogLock;
import liquibase.resource.ClassLoaderResourceAccessor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.security.AesUtil;
import org.broadinstitute.ddp.security.EncryptionKey;
import org.jdbi.v3.core.Handle;

@Slf4j
public class LiquibaseUtil implements AutoCloseable {
    public static final String PEPPER_APIS_GLOBAL_MIGRATIONS = "changelog-master.xml";
    public static final String HOUSEKEEPING_GLOBAL_MIGRATIONS = "housekeeping-changelog-master.xml";
    public static final String DSM_GLOBAL_MIGRATIONS = "master-changelog.xml";
    public static final String AUTH0_TENANT_MIGRATION = "db-changes/tenant-migration.xml";
    public static final String LIQUIBASE_TEST_CONTEXT = "new_db";
    public static final String LIQUIBASE_APP_CONTEXT = "existing_db";
    private HikariDataSource dataSource;

    private LiquibaseUtil(String dbUrl)  {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setMaximumPoolSize(2);
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
        runLiquibase(dbUrl, db, LIQUIBASE_APP_CONTEXT);
    }

    /**
     * Runs the global liquibase migrations against the given database url using a connection that's auto-closed
     *
     * @param context Liquibase context (required)
     */
    public static void runLiquibase(String dbUrl, DB db, String context) {
        if (context == null || context.isEmpty()) {
            throw new RuntimeException("Liquibase context must be provided");
        }

        try {
            try (LiquibaseUtil liquibaseUtil = new LiquibaseUtil(dbUrl)) {

                if (db == DB.APIS) {
                    liquibaseUtil.runPepperAPIsGlobalMigrations(context);
                } else if (db == DB.HOUSEKEEPING) {
                    liquibaseUtil.runHousekeepingGlobalMigrations(context);
                }  else if (db == DB.DSM) {
                    liquibaseUtil.runDSMGlobalMigrations(context);
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
                util.runMigrations(changeLogFile, LIQUIBASE_TEST_CONTEXT);
            }
        } catch (Exception e) {
            throw new DDPException("Error running liquibase migrations for changeLogFile " + changeLogFile, e);
        }
    }

    public static void releaseResources() {
        DatabaseFactory.setInstance(null);
    }

    /**
     * Runs the global migration scripts.  These are the scripts from {@link #PEPPER_APIS_GLOBAL_MIGRATIONS here}
     * which will be applied to test databases as well as production.
     *
     * @param context Liquibase context
     */
    private void runPepperAPIsGlobalMigrations(String context) throws LiquibaseException, SQLException {
        runMigrations(PEPPER_APIS_GLOBAL_MIGRATIONS, context);

        /* 2022.07.07
         * This call is required for proper operation of the DSS backend.
         * Without the Auth0 management client inserted by this call,
         * local user registration will fail to update the Auth0 user's
         * metadata.
         */
        insertLegacyTenant();

        // run script to update client -> tenant and study -> tenant and enable constraints
        runMigrations(AUTH0_TENANT_MIGRATION, context);
    }

    /**
     * Runs the global migration scripts.  These are the scripts from {@link #PEPPER_APIS_GLOBAL_MIGRATIONS here}
     * which will be applied to test databases as well as production.
     *
     * @param context Liquibase context
     * @throws LiquibaseException if something goes wrong
     */
    private void runHousekeepingGlobalMigrations(String context) throws LiquibaseException, SQLException {
        runMigrations(HOUSEKEEPING_GLOBAL_MIGRATIONS, context);
    }

    private void runDSMGlobalMigrations(String context) throws LiquibaseException, SQLException {
        runMigrations(DSM_GLOBAL_MIGRATIONS, context);
    }

    /**
     * Runs the specific changelog file. When migration fails, this will attempt to rollback the changes, as applicable.
     *
     * @param context Liquibase context
     */
    private void runMigrations(String changelogFile, String context) throws LiquibaseException, SQLException {
        if (context == null || context.isEmpty()) {
            throw new RuntimeException("Liquibase context must be provided");
        }

        try (Liquibase liquibase = new Liquibase(changelogFile, new ClassLoaderResourceAccessor(),
                new JdbcConnection(dataSource.getConnection()))) {
            String tag = null;
            try {
                logLocks(liquibase.listLocks());

                tag = generateDatabaseTag();
                liquibase.tag(tag);
                log.info("Tagged database with tag {}", tag);

                liquibase.update(new Contexts(context));
            } catch (LiquibaseException originalError) {
                log.error("LiquibaseException: {}", originalError.getMessage());
                if (tag != null) {
                    if (originalError.getCause().getClass() == MigrationFailedException.class
                            || originalError.getCause().getMessage().contains("Migration failed for change set " + changelogFile)) {
                        try {
                            log.info("Attempting to rollback changesets to tag {}", tag);
                            liquibase.rollback(tag, new Contexts());
                            log.info("Successfully rolled back changesets to tag {}", tag);
                        } catch (RollbackFailedException e) {
                            log.error("Failed to rollback changesets to tag {}, database might be in a bad state", tag, e);
                        }
                    }
                } else {
                    log.error("No liquibase object or tag to rollback changesets");
                }

                // Propagate original exception back up.
                throw originalError;
            } finally {
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

        if (!TransactionWrapper.isInitialized()) {
            TransactionWrapper.init(new TransactionWrapper.DbConfiguration(DB.APIS, 1, pepperApisDbUrl));
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
            jdbiAuth0Tenant.insertIfNotExists(domain, mgmtApiClient, encryptedSecret);
            log.info("Inserted testing tenant {}", domain);
            insertedTenant = true;
        } else {
            log.info("No legacy domain/mgt secret in config, skipping insert of auth0_tenant data");
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
                    log.info("Liquibase locked by {} at {}.  Lock id is {} ", dbLock.getLockedBy(),
                            dbLock.getLockGranted(), dbLock.getId());
                }
            }
        }
        if (!hasLocks) {
            log.info("No liquibase locks");
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
            log.warn("Unable to get hostname to create tag, defaulting to {}", hostname, e);
        }
        return String.format("%d-%s", Instant.now().toEpochMilli(), hostname);
    }
}

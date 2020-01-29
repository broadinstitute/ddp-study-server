package org.broadinstitute.ddp.db;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.typesafe.config.Config;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.exception.InvalidConfigurationException;
import org.broadinstitute.ddp.util.ConfigManager;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleCallback;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Singleton that grabs a connection and calls
 * inTransaction with the connection.  If no exception
 * is thrown, transaction is committed.  Otherwise, the transaction
 * is rolled back.  Any exceptions encountered
 * are re-thrown after making an attempt to rollback the transaction.
 */
public class TransactionWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionWrapper.class);

    private static final Map<DB, TransactionWrapper> gTxnWrapper = new HashMap<>();

    /**
     * Sleep duration in seconds between attempts to get a connection from
     * the pool when it looks like the password has changed.
     */
    private static final int PASSWORD_ROTATION_SLEEP = 5;

    /**
     * Maximum number of retries to make when it looks like the
     * database password has changed.
     */
    private static final int PASSWORD_ROTATION_MAX_RETRIES = 2;

    public static final String COULD_NOT_GET_CONNECTION = "Could not get a connection from the pool";

    private HikariDataSource dataSource;
    private final String dbUrl;
    private final Jdbi jdbi;

    private final int maxConnections;
    private static boolean isInitialized;

    public static synchronized boolean isInitialized() {
        return isInitialized;
    }

    public enum DB {

        APIS(ConfigFile.DB_URL, ConfigFile.NUM_POOLED_CONNECTIONS),
        HOUSEKEEPING(ConfigFile.HOUSEKEEPING_DB_URL, ConfigFile.HOUSEKEEPING_NUM_POOLED_CONNECTIONS);

        private final String dbUrlConfigKey;
        private final String poolSizeConfigKey;

        private DB(String dbUrlConfigKey, String poolSizeConfigKey) {
            this.dbUrlConfigKey = dbUrlConfigKey;
            this.poolSizeConfigKey = poolSizeConfigKey;
        }

        public String getDbUrlConfigKey() {
            return dbUrlConfigKey;
        }

        public String getDbPoolSizeConfigKey() {
            return poolSizeConfigKey;
        }
    }

    private TransactionWrapper(int maxConnections, String dbUrl, DB db) {
        this.dbUrl = dbUrl;
        this.maxConnections = maxConnections;
        if (dataSource != null) {
            dataSource.close();
        }
        dataSource = createDataSource(maxConnections, dbUrl, db);
        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    /**
     * Should only be called during testing.
     */
    public static synchronized void reset() {
        LOG.warn("reset() should only be called in the context of testing");
        for (TransactionWrapper transactionWrapper : gTxnWrapper.values()) {
            try {
                transactionWrapper.dataSource.close();
            } catch (Exception e) {
                LOG.error("Trouble while closing datasource for {}", transactionWrapper.dbUrl, e);
            }
        }
        gTxnWrapper.clear();
        isInitialized = false;
    }

    /**
     * Should only be called during testing to force connection pool
     * to create new connections on operations.
     */
    public static synchronized void closePool() {
        for (Map.Entry<DB, TransactionWrapper> db : gTxnWrapper.entrySet()) {
            db.getValue().dataSource.close();
        }
    }

    public static class DbConfiguration {

        private final int maxConnections;

        private final String dbUrl;

        private final DB db;

        public DbConfiguration(DB db, int maxConnections, String dbUrl) {
            this.db = db;
            this.maxConnections = maxConnections;
            this.dbUrl = dbUrl;
        }

        public int getMaxConnections() {
            return maxConnections;
        }

        public String getDbUrl() {
            return dbUrl;
        }

        public DB getDb() {
            return db;
        }
    }

    public static synchronized void init(String defaultTimeZoneName, DbConfiguration...dbConfigs) {
        if (dbConfigs.length == 0) {
            throw new IllegalArgumentException("must have at least one dbConfig");
        }

        for (DbConfiguration dbConfig : dbConfigs) {
            if (gTxnWrapper.containsKey(dbConfig.getDb())) {
                TransactionWrapper transactionWrapper = gTxnWrapper.get(dbConfig.getDb());
                int maxConnections = dbConfig.getMaxConnections();
                String dbUrl = dbConfig.getDbUrl();
                if (transactionWrapper.maxConnections != maxConnections || !transactionWrapper.dbUrl.equals(dbUrl)) {
                    throw new RuntimeException("init() has already been called with "
                            + transactionWrapper.maxConnections + " and "
                            + transactionWrapper.dbUrl + "; " +  "you cannot re-initialize "
                            + "it with different params " + maxConnections
                            + " and " + dbUrl);
                } else {
                    LOG.warn("TransactionWrapper has already been initialized.");
                }
            }
            gTxnWrapper.put(dbConfig.getDb(), new TransactionWrapper(dbConfig.getMaxConnections(), dbConfig.getDbUrl(), dbConfig.getDb()));
        }
        // We are setting the VMs default timezone expectation that it will be same on server
        // or at least specified in DB connection URL
        // if timezones don't match, times and dates might be miscalculated/improperly converted by JDBC connector
        // https://bugs.mysql.com/bug.php?id=91112
        LOG.info("Setting default time zone to: {}", defaultTimeZoneName);
        TimeZone defaultTimeZone = TimeZone.getTimeZone(defaultTimeZoneName);
        if (defaultTimeZone == null) {
            throw new RuntimeException(String.format("Could not find a Time zone with name: %s", defaultTimeZoneName));
        }
        TimeZone.setDefault(defaultTimeZone);

        isInitialized = true;
    }

    /**
     * If there's only a single db initialized, return the {@link DB} for it.  Otherwise
     * an exception is thrown.
     */
    public static synchronized DB getDB() {
        if (gTxnWrapper.size() == 1) {
            return gTxnWrapper.keySet().iterator().next();
        } else {
            throw new IllegalStateException("There are " + gTxnWrapper.size() + " dbs initialized, "
                    + "please indicate which one to use.");
        }
    }

    /**
     * If there's only a single db initialized, return the {@link TransactionWrapper} for it.  Otherwise
     * an exception is thrown.
     */
    public static synchronized TransactionWrapper getInstance() {
        return getInstance(getDB());
    }

    public static synchronized TransactionWrapper getInstance(DB db) {
        if (!gTxnWrapper.containsKey(db)) {
            throw new IllegalStateException("Please call init() first.");
        }
        return gTxnWrapper.get(db);
    }

    /**
     * Re-reads the database pool configuration values.
     * @param useCache if true, pool is initialized with cached config values.  If false, config file is re-read from underlying storage.
     * @throws InvalidConfigurationException if the configuration is invalid and cannot be used to create a new pool
     */
    public static synchronized void reloadDbPoolConfiguration(boolean useCache) throws InvalidConfigurationException {
        // save current list of DBs before clearing
        var dbs = new ArrayList<DB>();
        dbs.addAll(gTxnWrapper.keySet());

        Config cfg = ConfigManager.getInstance().getConfig();
        if (!useCache) {
            cfg = ConfigManager.parseConfig();
        }

        // check configuration values first.  If they're no good, we don't want to
        // silently get the pool into  an invalid state
        StringBuilder invalidConfigMessage  = new StringBuilder();
        for (DB db : dbs) {
            if (!cfg.hasPath(db.getDbUrlConfigKey())) {
                invalidConfigMessage.append("Config does not have " + db.getDbUrlConfigKey() + " for " + getDB());
            }
            if  (!cfg.hasPath(db.getDbPoolSizeConfigKey())) {
                invalidConfigMessage.append("Config does not have " + db.getDbPoolSizeConfigKey() + " for " + getDB());
            }
        }

        if (invalidConfigMessage.length() > 0) {
            throw new InvalidConfigurationException(invalidConfigMessage.toString());
        }

        reset();

        // now add configurations after re-reading them
        var dbConfigs = new ArrayList<DbConfiguration>();
        for (DB db : dbs) {
            String dbUrl = cfg.getString(db.getDbUrlConfigKey());
            dbConfigs.add(new DbConfiguration(db, cfg.getInt(db.getDbPoolSizeConfigKey()), dbUrl));
        }
        init(TimeZone.getDefault().getID(), dbConfigs.toArray(new DbConfiguration[0]));
    }

    /**
     * Returns true if the connectivity issue appears to be related
     * to credentials.
     */
    private static boolean isAuthException(ConnectionException e) {
        boolean isAuthRelated = false;
        if (e.getCause() != null) {
            isAuthRelated = e.getCause().getMessage().toLowerCase().contains("access denied for user".toLowerCase());
        }
        return isAuthRelated;
    }

    /**
     * Use a Jdbi handle within a transaction and return a value.
     *
     * @param callback Accepts a handle and returns a value
     * @param <R>      Type of return value from callback
     * @param <X>      Type of exception thrown by callback
     * @param db the database to use
     * @return value returned by callback
     * @throws X            exception thrown by callback
     * @throws DDPException if error getting a raw connection
     */
    public static <R, X extends Exception> R withTxn(DB db, HandleCallback<R, X> callback) throws X {
        try {
            try (Handle h = openJdbiWithAuthRetry(db)) {
                return h.inTransaction(callback);
            }
        } catch (ConnectionException e) {
            throw new DDPException(COULD_NOT_GET_CONNECTION, e);
        }
    }

    /**
     * Use a Jdbi handle within a transaction and return a value.  If more than one database has been
     * initialized, an exception is thrown.  To identify which database to use,
     * call {@link #withTxn(DB, HandleCallback)} to disambiguate.
     *
     * @param callback Accepts a handle and returns a value
     * @param <R>      Type of return value from callback
     * @param <X>      Type of exception thrown by callback
     * @return value returned by callback
     * @throws X            exception thrown by callback
     * @throws DDPException if error getting a raw connection
     */
    public static <R, X extends Exception> R withTxn(HandleCallback<R, X> callback) throws X {
        try {
            try (Handle h = openJdbiWithAuthRetry(getDB())) {
                return h.inTransaction(callback);
            }
        } catch (ConnectionException e) {
            throw new DDPException(COULD_NOT_GET_CONNECTION, e);
        }
    }

    /**
     * Calls {@link Jdbi#open()}, but wraps it in retries that re-read config
     * values so that we can rotate the password without rebooting the app.
     */
    private static Handle openJdbiWithAuthRetry(DB db) {
        Handle h;
        for (int tryCount = 0; tryCount < PASSWORD_ROTATION_MAX_RETRIES; tryCount++) {
            try {
                return getInstance(db).jdbi.open();
            } catch (ConnectionException e) {
                if (isAuthException(e)) {
                    LOG.info("Database pool credentials have been rejected; pausing and reloading creds from config file.", e);
                    try {
                        TimeUnit.SECONDS.sleep(PASSWORD_ROTATION_SLEEP);
                    } catch (InterruptedException interrupted) {
                        LOG.error("Sleep before dbpool credential reload has been interrupted", interrupted);
                    }
                    reloadDbPoolConfiguration(false);
                } else {
                    throw new DDPException(COULD_NOT_GET_CONNECTION, e);
                }
            } catch (InvalidConfigurationException e) {
                LOG.error("Database connection configuration is invalid.  Proceeding with original configuration values.");
            }
        }
        // if here, we've tried a few times, but are still unable to get a connection.
        throw new DDPException(COULD_NOT_GET_CONNECTION);
    }

    /**
     * Use a Jdbi handle within a transaction.  If more than one database has been
     * initialized, an exception is thrown.  To identify which database to use,
     * call {@link #useTxn(DB, HandleConsumer)} to disambiguate.
     * @param <X> Type of exception thrown by callback
     * @param callback Accepts a handle  @throws X exception thrown by callback
     * @throws DDPException if error getting a raw connection
     */
    public static <X extends Exception> void useTxn(DB db, HandleConsumer<X> callback) throws X {
        try (Handle h = openJdbiWithAuthRetry(db)) {
            h.useTransaction(callback);
        } catch (ConnectionException e) {
            throw new DDPException(COULD_NOT_GET_CONNECTION, e);
        }
    }

    /**
     * Use a Jdbi handle within a transaction.  If more than one database has been
     * initialized, an exception is thrown.  To identify which database to use,
     * call {@link #useTxn(DB, HandleConsumer)} to disambiguate.
     * @param <X> Type of exception thrown by callback
     * @param callback Accepts a handle  @throws X exception thrown by callback
     * @throws DDPException if error getting a raw connection
     */
    public static <X extends Exception> void useTxn(HandleConsumer<X> callback) throws X {
        try (Handle h = openJdbiWithAuthRetry(getDB())) {
            h.useTransaction(callback);
        } catch (ConnectionException e) {
            throw new DDPException(COULD_NOT_GET_CONNECTION, e);
        }
    }

    private HikariDataSource createDataSource(int maxConnections, String dbUrl, DB db) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.setInitializationFailTimeout(0); // don't fail to  create the pool if the db is not available
        config.setAutoCommit(true); // will be managed by jdbi, which expects autcommit to be enabled initially
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setMaximumPoolSize(maxConnections);
        config.setConnectionTimeout(250);
        config.setMaxLifetime((28800 * 1000) - (3 * 60 * 1000));  // as per hikari docs, a few minutes less than mysql wait_timeout
        config.setPoolName(db.name());

        // todo arz leverage allowPoolSuspension and mxbeans to fully  automate password rotation

        return new HikariDataSource(config);
    }


    /*
    public static PoolingDataSource<PoolableConnection> createDataSource(int maxConnections, String dbUrl) {
        org.apache.commons.dbcp2.ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(dbUrl, null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setDefaultAutoCommit(true); // will be managed by jdbi
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setBlockWhenExhausted(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinIdle(5);
        poolConfig.setMinEvictableIdleTimeMillis(TimeUnit.MINUTES.toMillis(5));
        poolConfig.setTimeBetweenEvictionRunsMillis(TimeUnit.HOURS.toMillis(1));
        poolableConnectionFactory.setValidationQueryTimeout(1);
        poolableConnectionFactory.setMaxConnLifetimeMillis(TimeUnit.HOURS.toMillis(3));
        ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory, poolConfig);
        poolableConnectionFactory.setPool(connectionPool);
        return new PoolingDataSource<>(connectionPool);
    }
    */



}


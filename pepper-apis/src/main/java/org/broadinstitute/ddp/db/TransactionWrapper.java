package org.broadinstitute.ddp.db;


import com.netflix.servo.DefaultMonitorRegistry;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.NumberGauge;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.ddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * Singleton that grabs a connection and calls
 * inTransaction with the connection.  If no exception
 * is thrown, transaction is committed.  Otherwise, the transaction
 * is rolled back.  Any exceptions encountered
 * are re-thrown after making an attempt to rollback the transaction.
 */
public class TransactionWrapper {

    //monitoring
    private static final AtomicInteger dbErrors = new AtomicInteger(0);
    private static final NumberGauge dbErrorGauge = new NumberGauge(MonitorConfig.builder("db_error_gauge").build(), dbErrors);

    //explicitly wire up the metrics using a static initializer
    static {
        DefaultMonitorRegistry.getInstance().register(dbErrorGauge);
    }

    private static final Logger logger = LoggerFactory.getLogger(TransactionWrapper.class);

    private static HashMap<String, TransactionWrapper> gTxnWrapper = new HashMap<>();

    private static boolean sslConfigured = false;

    private PoolingDataSource dataSource;
    private Config conf;

    public static final String SINGLE_SOURCE = "SINGLESOURCE";
    private static final String SESSION_VARIABLES = "&sessionVariables=innodb_strict_mode=on,tx_isolation='READ-COMMITTED',sql_mode='TRADITIONAL'";

    private TransactionWrapper(int maxConnections, String dbUrl, Config conf, boolean skipSsl) {
        this.dataSource = createDataSource(maxConnections, dbUrl, skipSsl);
        this.conf = conf;
    }

    public static synchronized void reset(String appEnv) {
        reset(appEnv, SINGLE_SOURCE);
    }

    public static synchronized void reset(String appEnv, String sourceName)
    {
        if (!appEnv.equals(Utility.Deployment.UNIT_TEST.toString()))
        {
            throw new RuntimeException("Reset is only for testing.");
        }
        gTxnWrapper.put(sourceName, null);
    }

    public static synchronized void init(@NonNull int maxConnections, @NonNull String dbUrl, @NonNull Config config, boolean skipSsl) {
        init(maxConnections, dbUrl, SINGLE_SOURCE, config, skipSsl);
    }

    public static synchronized void init(int maxConnections, String dbUrl, String sourceName, @NonNull Config config, boolean skipSsl) {
        if (gTxnWrapper.get(sourceName) != null) {
            throw new RuntimeException("Init has already been called.");
        }
        else {
            if ((!skipSsl)&&(!sslConfigured)) {
                throw new RuntimeException("configureSslProperties() must be called first before using SSL connections. " +
                        "It should be one of the first things the application does at startup.");
            }
            gTxnWrapper.put(sourceName, new TransactionWrapper(maxConnections, dbUrl, config, skipSsl));
        }
    }

    public static synchronized void configureSslProperties(@NonNull String keyStore, @NonNull String keyStorePwd,
                                                           @NonNull String trustStore, @NonNull String trustStorePwd) {
        if (!sslConfigured) {
            logger.info("Setting system SSL properties.");
            System.setProperty("javax.net.ssl.keyStore", keyStore);
            System.setProperty("javax.net.ssl.keyStorePassword", keyStorePwd);
            System.setProperty("javax.net.ssl.trustStore", trustStore);
            System.setProperty("javax.net.ssl.trustStorePassword", trustStorePwd);
            sslConfigured = true;
        }
    }

    public static synchronized TransactionWrapper getInstance(@NonNull String sourceName) {
        if (gTxnWrapper == null) {
            throw new RuntimeException("Please call init() first.");
        }
        return gTxnWrapper.get(sourceName);
    }

    public interface InTransaction<T> {
        public T inTransaction(Connection conn);
    }

    public static String getSqlFromConfig(@NonNull String queryName) {
        return getSqlFromConfig(SINGLE_SOURCE, queryName);
    }

    public static boolean hasConfigPath(@NonNull String configPath) {
        Config conf = getInstance(SINGLE_SOURCE).conf;

        if (conf == null) {
            throw new RuntimeException("Conf has not been configured");
        }

        return conf.hasPath(configPath);
    }

    public static String getSqlFromConfig(@NonNull String sourceName, @NonNull String queryName) {
        Config conf = getInstance(sourceName).conf;

        if (conf == null) {
            throw new RuntimeException("Conf has not been configured for source = " + sourceName);
        }

        if (!conf.hasPath(queryName)) {
            throw new RuntimeException("Conf is missing query named " + queryName);
        }

        return conf.getString(queryName);
    }

    // todo arz test with good sql and with bad sql
    // run in separate thread with a long wait() in
    // an inTransaction() method, have first thread
    // hit a query at least once to confirm that
    // no data has changed in their read of the data

    public static <T> T inTransaction(InTransaction<T> inTransaction)  {
        return inTransaction(inTransaction, SINGLE_SOURCE);
    }

    public static <T> T inTransaction(InTransaction<T> inTransaction, String sourceName)  {
        T returnedValue = null;
        PoolingDataSource db = getInstance(sourceName).dataSource;
        try (Connection conn = db.getConnection()) {
            logger.debug("retrieved connection...");
            boolean hadError = false;

            try {
                returnedValue = inTransaction.inTransaction(conn);
            } catch (Throwable t) {
                hadError = true;
                try {
                    conn.rollback();
                    throw t;
                } catch (SQLException e) {
                    throw new RuntimeException("Could not rollback transaction", e);
                }
            } finally {
                if (!hadError) {
                    try {
                        conn.commit();
                    } catch (SQLException e) {
                        throw new RuntimeException("Error committing transaction",e);
                    }
                }
                else {
                    logger.info("Total number of db errors logged since application start = " + dbErrors.incrementAndGet());
                }
            }
        }
        catch(SQLException e) {
            throw new RuntimeException("Error closing connection",e);
        }

        return returnedValue;
    }

    private PoolingDataSource<PoolableConnection> createDataSource(int maxConnections, String dbUrl, boolean skipSsl) {
        String extraVariables = (skipSsl ? "" : "&verifyServerCertificate=true&useSSL=true&requireSSL=true") + SESSION_VARIABLES;

        logger.warn("**IMPORTANT** This DB connection url will include " + extraVariables);

        dbUrl += extraVariables;

        org.apache.commons.dbcp2.ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(dbUrl,null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setDefaultAutoCommit(false);
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setBlockWhenExhausted(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinIdle(5);
        poolConfig.setMinEvictableIdleTimeMillis(60 * 1000);
        poolableConnectionFactory.setValidationQueryTimeout(1);
        ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool<>(poolableConnectionFactory,poolConfig);
        poolableConnectionFactory.setPool(connectionPool);
        PoolingDataSource<PoolableConnection> dataSource = new PoolingDataSource<>(connectionPool);

        return dataSource;
    }
}
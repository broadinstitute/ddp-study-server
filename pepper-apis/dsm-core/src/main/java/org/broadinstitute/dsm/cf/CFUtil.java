package org.broadinstitute.dsm.cf;

import java.io.IOException;
import java.util.logging.Logger;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretPayload;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

public class CFUtil {

    private static final Logger logger = Logger.getLogger(CFUtil.class.getName());

    // can be shared across invocations, making things like TransactionWrapper.init() hard to predict.
    public static PoolingDataSource<PoolableConnection> createDataSource(int maxConnections, String dbUrl) {
        ConnectionFactory connectionFactory = new DriverManagerConnectionFactory(dbUrl, null);
        PoolableConnectionFactory poolableConnectionFactory = new PoolableConnectionFactory(connectionFactory, null);
        poolableConnectionFactory.setDefaultAutoCommit(false);
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(maxConnections);
        poolConfig.setTestOnBorrow(false);
        poolConfig.setBlockWhenExhausted(false);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinIdle(5);
        poolConfig.setMinEvictableIdleTimeMillis(60000L);
        poolableConnectionFactory.setValidationQueryTimeout(1);
        ObjectPool<PoolableConnection> connectionPool = new GenericObjectPool(poolableConnectionFactory, poolConfig);
        poolableConnectionFactory.setPool(connectionPool);
        PoolingDataSource<PoolableConnection> dataSource = new PoolingDataSource(connectionPool);
        return dataSource;
    }

    public static Config loadConfig(String projectId, String secretId) throws IOException  {
        logger.info("Looking up secrets from project " + projectId + " and secret " + secretId);
        try (SecretManagerServiceClient secretManagerServiceClient = SecretManagerServiceClient.create()) {
            var latestSecretVersion = SecretVersionName.of(projectId, secretId, "latest");
            SecretPayload secret = secretManagerServiceClient.accessSecretVersion(latestSecretVersion).getPayload();
            String secretData = secret.getData().toStringUtf8();
            return ConfigFactory.parseString(secretData);
        }
    }

    public static Config loadConfig() throws IOException {
        String projectId = System.getenv("PROJECT_ID");
        String secretId = System.getenv("SECRET_ID");

        return loadConfig(projectId, secretId);
    }
}

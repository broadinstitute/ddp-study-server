package org.broadinstitute.ddp.migration;

import java.util.concurrent.TimeUnit;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.ConnectionException;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.HandleConsumer;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DsmDataLoader {

    private static final Logger LOG = LoggerFactory.getLogger(DsmDataLoader.class);
    private static Jdbi jdbi;

    public static void initDatabasePool(String dsmDbUrl, int maxConnections) {
        HikariDataSource dataSource = createDataSource(dsmDbUrl, maxConnections);
        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    private static HikariDataSource createDataSource(String dsmDbUrl, int maxConnections) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dsmDbUrl);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("useCursorFetch", "true");
        config.setInitializationFailTimeout(0); // don't fail to  create the pool if the db is not available
        config.setAutoCommit(true); // will be managed by jdbi, which expects autcommit to be enabled initially
        config.setTransactionIsolation("TRANSACTION_READ_COMMITTED");
        config.setMaximumPoolSize(maxConnections);
        config.setConnectionTimeout(TimeUnit.SECONDS.toMillis(5));
        config.setMaxLifetime(TimeUnit.SECONDS.toMillis(14400)); // 4 hours, which is half the default wait_timeout of mysql
        config.setPoolName("dsm-pool");
        return new HikariDataSource(config);
    }

    public static <X extends Exception> void useTxn(HandleConsumer<X> callback) throws X {
        try (Handle h = jdbi.open()) {
            h.useTransaction(callback);
        } catch (ConnectionException e) {
            throw new LoaderException(e);
        }
    }

    public void saveData(Handle dsmHandle, String studyGuid, String jsonData) {
    }

    interface DsmDao extends SqlObject {
        @GetGeneratedKeys
        @SqlUpdate("")
        long insertParticipantData(
                @Bind("studyGuid") String studyGuid,
                @Bind("participantGuid") String participantGuid,
                @Bind("json") String jsonData,
                @Bind("fieldType") String fieldType,
                @Bind("lastChanged") long lastChanged,
                @Bind("lastChangedBy") String lastChangedBy);
    }
}

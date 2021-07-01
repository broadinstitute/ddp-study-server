package org.broadinstitute.ddp.migration;

import java.time.Instant;
import java.util.Optional;
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
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public class DsmDataLoader {

    private static Jdbi jdbi;

    public static void initDatabasePool(String dsmDbUrl, int maxConnections) {
        HikariDataSource dataSource = createDataSource(dsmDbUrl, maxConnections);
        jdbi = Jdbi.create(dataSource);
        jdbi.installPlugin(new SqlObjectPlugin());
    }

    private static HikariDataSource createDataSource(String dsmDbUrl, int maxConnections) {
        // Configuration here is taken mostly from Pepper's TransactionWrapper.
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

    public long createDsmParticipant(Handle dsmHandle, String studyGuid, String participantAltPid) {
        return dsmHandle.attach(DsmDao.class).insertParticipant(
                studyGuid, participantAltPid, Instant.now().toEpochMilli());
    }

    public Long findDsmParticipantId(Handle dsmHandle, String studyGuid, String participantAltPid) {
        return dsmHandle.attach(DsmDao.class).findParticipantId(studyGuid, participantAltPid).orElse(null);
    }

    public long createParticipantRecord(Handle dsmHandle, String studyGuid, long dsmParticipantId, String jsonData) {
        return dsmHandle.attach(DsmDao.class).insertParticipantRecord(
                studyGuid, dsmParticipantId, jsonData, Instant.now().toEpochMilli());
    }

    public void updateParticipantRecord(Handle dsmHandle, long dsmParticipantId, String jsonData) {
        int numUpdated = dsmHandle.attach(DsmDao.class).updateParticipantRecord(dsmParticipantId, jsonData);
        if (numUpdated != 1) {
            throw new LoaderException("Expected to update 1 row but did " + numUpdated);
        }
    }

    public long loadFormData(Handle dsmHandle, String studyGuid, String participantAltPid, String jsonData) {
        return dsmHandle.attach(DsmDao.class).insertDynamicFormData(
                studyGuid, participantAltPid, jsonData, Instant.now().toEpochMilli());
    }

    interface DsmDao extends SqlObject {
        @GetGeneratedKeys
        @SqlUpdate("insert into ddp_participant"
                + "        (ddp_participant_id, ddp_instance_id, last_changed, changed_by)"
                + " select :participantAltPid, ddp_instance_id, :lastChanged, 'SYSTEM'"
                + "   from ddp_instance where study_guid = :studyGuid")
        long insertParticipant(
                @Bind("studyGuid") String studyGuid,
                @Bind("participantAltPid") String participantAltPid,
                @Bind("lastChanged") long lastChangedMillis);

        @SqlQuery("select participant_id from ddp_participant where ddp_participant_id = :participantAltPid"
                + "   and ddp_instance_id = (select ddp_instance_id from ddp_instance where study_guid = :studyGuid)")
        Optional<Long> findParticipantId(
                @Bind("studyGuid") String studyGuid,
                @Bind("participantAltPid") String participantAltPid);

        @GetGeneratedKeys
        @SqlUpdate("insert into ddp_participant_record"
                + "        (participant_id, additional_values_json, last_changed, changed_by)"
                + " values (:participantId, :json, :lastChanged, 'SYSTEM')")
        long insertParticipantRecord(
                @Bind("studyGuid") String studyGuid,
                @Bind("participantId") long dsmParticipantId,
                @Bind("json") String additionalValuesJson,
                @Bind("lastChanged") long lastChangedMillis);

        @SqlUpdate("update ddp_participant_record"
                + "    set additional_values_json = :json"
                + "  where participant_id = :participantId")
        int updateParticipantRecord(
                @Bind("participantId") long dsmParticipantId,
                @Bind("json") String additionalValuesJson);

        @GetGeneratedKeys
        @SqlUpdate("insert into ddp_participant_data"
                + "        (ddp_participant_id, ddp_instance_id, field_type_id, data, last_changed, changed_by)"
                + " select :participantAltPid, ddp_instance_id, 'RGP_PARTICIPANTS', :json, :lastChanged, 'SYSTEM'"
                + "   from ddp_instance where study_guid = :studyGuid")
        long insertDynamicFormData(
                @Bind("studyGuid") String studyGuid,
                @Bind("participantAltPid") String participantAltPid,
                @Bind("json") String jsonData,
                @Bind("lastChanged") long lastChangedMillis);
    }
}

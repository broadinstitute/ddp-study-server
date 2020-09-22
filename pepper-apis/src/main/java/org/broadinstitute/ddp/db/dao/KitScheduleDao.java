package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.kit.KitScheduleRecord;
import org.broadinstitute.ddp.model.kit.PendingScheduleRecord;
import org.jdbi.v3.core.mapper.EnumByOrdinalMapperFactory;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapperFactory;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.FetchSize;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface KitScheduleDao {

    @CreateSqlObject
    KitScheduleSql getKitScheduleSql();

    default void createSchedule(KitSchedule schedule) {
        DBUtils.checkInsert(1, getKitScheduleSql().insertSchedule(
                schedule.getConfigId(),
                schedule.getNumOccurrencesPerUser(),
                schedule.getNextTimeAmount(),
                schedule.getNextPrepTimeAmount(),
                schedule.getOptOutExpr(),
                schedule.getIndividualOptOutExpr()));
    }

    @SqlQuery("select * from kit_schedule where kit_configuration_id = :configId")
    @RegisterConstructorMapper(KitSchedule.class)
    Optional<KitSchedule> findSchedule(@Bind("configId") long kitConfigurationId);


    default long createScheduleRecord(long userId, long kitConfigurationId, long initialKitRequestId) {
        return getKitScheduleSql().insertRecord(
                userId, kitConfigurationId, false, 0, initialKitRequestId, null, null);
    }

    default void updateRecordOptOut(long recordId, boolean optedOut) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordOptOut(recordId, optedOut));
    }

    @Deprecated
    default void updateRecordInitialKitSentTime(long recordId, Instant sentTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordInitialKitSentTime(recordId, sentTime));
    }

    @Deprecated
    default void updateRecordInitialKitSentTimes(List<Long> recordIds, List<Instant> sentTimes) {
        if (recordIds.size() != sentTimes.size()) {
            throw new DaoException("List size for record ids and sent times must match");
        }
        int[] updated = getKitScheduleSql().bulkUpdateRecordInitialKitSentTime(recordIds, sentTimes);
        DBUtils.checkUpdate(recordIds.size(), Arrays.stream(updated).sum());
    }

    default void updateRecordCurrentOccurrencePrepTime(long recordId, Instant currentOccurrencePrepTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordCurrentOccurrencePrepTime(recordId, currentOccurrencePrepTime));
    }

    default void incrementRecordNumOccurrence(long recordId) {
        DBUtils.checkUpdate(1, getKitScheduleSql().incrementRecordNumOccurrences(recordId, Instant.now()));
    }

    @SqlQuery("select rec.*,"
            + "       (select kit_request_guid from kit_request"
            + "         where kit_request_id = rec.initial_kit_request_id) as initial_kit_request_guid"
            + "  from kit_schedule_record as rec"
            + " where rec.kit_schedule_record_id = :id")
    @RegisterConstructorMapper(KitScheduleRecord.class)
    Optional<KitScheduleRecord> findRecord(@Bind("id") long recordId);

    @Deprecated
    @UseStringTemplateSqlLocator
    @SqlQuery("findAllEligibleRecordsWaitingForKitStatus")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    Stream<PendingScheduleRecord> findAllEligibleRecordsWaitingForKitStatus();

    @UseStringTemplateSqlLocator
    @SqlQuery("findPendingScheduleRecords")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    @FetchSize(10_000)
    Stream<PendingScheduleRecord> findPendingScheduleRecords(@Bind("configId") long kitConfigurationId);
}

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
import org.broadinstitute.ddp.service.KitCheckService;
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

    //
    // Kit schedule
    //

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

    //
    // Schedule record
    //

    default long createScheduleRecord(long userId, long kitConfigurationId) {
        return getKitScheduleSql().insertRecord(
                userId, kitConfigurationId, false, 0, null, null, null);
    }

    default void updateRecordOptOut(long recordId, boolean optedOut) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordOptOut(recordId, optedOut));
    }

    default void updateRecordInitialKitSentTime(long recordId, Instant sentTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordInitialKitSentTime(recordId, sentTime));
    }

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

    @SqlQuery("select * from kit_schedule_record where kit_schedule_record_id = :id")
    @RegisterConstructorMapper(KitScheduleRecord.class)
    Optional<KitScheduleRecord> findRecord(@Bind("id") long recordId);

    @SqlQuery("select * from kit_schedule_record where participant_user_id = :userId and kit_configuration_id = :kitConfigId")
    @RegisterConstructorMapper(KitScheduleRecord.class)
    Optional<KitScheduleRecord> findRecord(@Bind("userId") long participantUserId, @Bind("kitConfigId") long kitConfigurationId);

    @UseStringTemplateSqlLocator
    @SqlQuery("findPendingScheduleRecords")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    @FetchSize(KitCheckService.DEFAULT_QUERY_FETCH_SIZE)
    Stream<PendingScheduleRecord> findPendingScheduleRecords(@Bind("configId") long kitConfigurationId);
}

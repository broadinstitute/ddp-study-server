package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.kit.PendingScheduleRecord;
import org.jdbi.v3.core.mapper.EnumByOrdinalMapperFactory;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapperFactory;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

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


    default long createInitialScheduleRecord(long userId, long kitConfigurationId, Long lastKitRequestId) {
        return getKitScheduleSql().insertRecord(
                userId, kitConfigurationId, false, 0, Instant.now(), lastKitRequestId, null, null);
    }

    default void updateRecordOptOut(long recordId, boolean optedOut) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordOptOut(recordId, optedOut));
    }

    default void updateRecordLastKitSentTime(long recordId, Instant sentTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordLastKitSentTime(recordId, sentTime));
    }

    default void updateRecordLastKitSentTimes(List<Long> recordIds, List<Instant> sentTimes) {
        if (recordIds.size() != sentTimes.size()) {
            throw new DaoException("List size for record ids and sent times must match");
        }
        int[] updated = getKitScheduleSql().bulkUpdateRecordLastKitSentTime(recordIds, sentTimes);
        DBUtils.checkUpdate(recordIds.size(), Arrays.stream(updated).sum());
    }

    default void updateRecordCurrentOccurrencePrepTime(long recordId, Instant currentOccurrencePrepTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordCurrentOccurrencePrepTime(recordId, currentOccurrencePrepTime));
    }

    default void incrementRecordNumOccurrenceWithoutKit(long recordId) {
        DBUtils.checkUpdate(1, getKitScheduleSql().incrementRecordNumOccurrences(recordId, Instant.now(), null));
    }

    default void incrementRecordNumOccurrenceWithKit(long recordId, long kitRequestId) {
        DBUtils.checkUpdate(1, getKitScheduleSql().incrementRecordNumOccurrences(recordId, Instant.now(), kitRequestId));
    }

    @SqlQuery("select usen.study_id,"
            + "       (select guid from umbrella_study where umbrella_study_id = usen.study_id) as study_guid,"
            + "       (select guid from user where user_id = usen.user_id) as user_guid,"
            + "       addr.address_id,"
            + "       vs.code as address_validation_status,"
            + "       rec.*,"
            + "       (select kit_request_guid from kit_request where kit_request_id = rec.last_kit_request_id) as last_kit_request_guid"
            + "  from kit_schedule_record as rec"
            + "  join user_study_enrollment as usen on usen.user_id = rec.participant_user_id"
            + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
            + "  join kit_configuration as kc on kc.kit_configuration_id = rec.kit_configuration_id and kc.study_id = usen.study_id"
            + "  join kit_schedule as ks on ks.kit_configuration_id = kc.kit_configuration_id"
            + "  left join default_mailing_address as defaddr on defaddr.participant_user_id = usen.user_id"
            + "  left join mailing_address as addr on addr.address_id = defaddr.address_id"
            + "  left join mailing_address_validation_status as vs on vs.mailing_address_validation_status_id = addr.validation_status_id"
            + " where entype.enrollment_status_type_code = 'ENROLLED'"
            + "   and usen.valid_to is null"
            + "   and rec.opted_out = false"
            + "   and rec.num_occurrences < ks.num_occurrences_per_user"
            + "   and rec.last_kit_request_id is not null"
            + " order by usen.study_id")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    Stream<PendingScheduleRecord> findAllEligibleRecordsWaitingForKitStatus();

    @SqlQuery("select usen.study_id,"
            + "       (select guid from umbrella_study where umbrella_study_id = usen.study_id) as study_guid,"
            + "       (select guid from user where user_id = usen.user_id) as user_guid,"
            + "       addr.address_id,"
            + "       vs.code as address_validation_status,"
            + "       rec.*,"
            + "       (select kit_request_guid from kit_request where kit_request_id = rec.last_kit_request_id) as last_kit_request_guid"
            + "  from kit_schedule_record as rec"
            + "  join user_study_enrollment as usen on usen.user_id = rec.participant_user_id"
            + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
            + "  join kit_configuration as kc on kc.kit_configuration_id = rec.kit_configuration_id and kc.study_id = usen.study_id"
            + "  join kit_schedule as ks on ks.kit_configuration_id = kc.kit_configuration_id"
            + "  left join default_mailing_address as defaddr on defaddr.participant_user_id = usen.user_id"
            + "  left join mailing_address as addr on addr.address_id = defaddr.address_id"
            + "  left join mailing_address_validation_status as vs on vs.mailing_address_validation_status_id = addr.validation_status_id"
            + " where entype.enrollment_status_type_code = 'ENROLLED'"
            + "   and usen.valid_to is null"
            + "   and rec.kit_configuration_id = :configId"
            + "   and rec.opted_out = false"
            + "   and rec.num_occurrences < ks.num_occurrences_per_user")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    Stream<PendingScheduleRecord> findPendingScheduleRecords(@Bind("configId") long kitConfigurationId);
}

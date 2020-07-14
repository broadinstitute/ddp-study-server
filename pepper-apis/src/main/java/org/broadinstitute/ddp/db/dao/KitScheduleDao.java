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
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.jdbi.v3.core.mapper.EnumByOrdinalMapperFactory;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
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

    @SqlQuery("select rec.*,"
            + "       user.user_guid as participant_user_guid,"
            + "       (select kit_request_guid from kit_request where kit_request_id = rec.last_kit_request_id) as last_kit_request_guid"
            + "  from user_study_enrollment as usen"
            + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
            + "  join user on user.user_id = usen.user_id"
            + "  join kit_schedule_record as rec on rec.participant_user_id = user.user_id"
            + "  join kit_schedule as ks on ks.kit_configuration_id = rec.kit_configuration_id"
            + " where entype.enrollment_status_type_code = 'ENROLLED'"
            + "   and valid_to is null"
            + "   and rec.opted_out = false"
            + "   and rec.num_occurrences < ks.num_occurrences_per_user,"
            + "   and rec.last_kit_request_id is not null"
            + "   and rec.last_kit_sent_time is null")
    @RegisterConstructorMapper(KitScheduleRecord.class)
    Stream<KitScheduleRecord> findAllEligibleRecordsWaitingForKitStatus();

    @SqlQuery("select addr.address_id,"
            + "       vs.code as address_validation_status,"
            + "       usen.study_id,"
            + "       rec.*,"
            + "       user.user_guid as participant_user_guid,"
            + "       (select kit_request_guid from kit_request where kit_request_id = rec.last_kit_request_id) as last_kit_request_guid"
            + "  from user_study_enrollment as usen"
            + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
            + "  join user on user.user_id = usen.user_id"
            + "  join kit_configuration as kc on kc.study_id = usen.study_id"
            + "  join kit_schedule as ks on ks.kit_configuration_id = kc.kit_configuration_id"
            + "  join kit_schedule_record as rec"
            + "       on rec.participant_user_id = user.user_id"
            + "       and rec.kit_configuration_id = kc.kit_configuration_id"
            + "  left join default_mailing_address as defaddr on defaddr.participant_user_id = user.user_id"
            + "  left join mailing_address as addr on addr.address_id = defaddr.address_id"
            + "  left join mailing_address_validation_status as vs on vs.mailing_address_validation_status_id = addr.validation_status_id"
            + " where entype.enrollment_status_type_code = 'ENROLLED'"
            + "   and valid_to is null"
            + "   and rec.kit_configuration_id = :configId"
            + "   and rec.opted_out = false"
            + "   and rec.num_occurrences < ks.num_occurrences_per_user")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    Stream<PendingScheduleRecord> findPendingScheduleRecords(@Bind("configId") long kitConfigurationId);

    class PendingScheduleRecord {
        private long studyId;
        private Long addressId;
        private DsmAddressValidationStatus addressValidationStatus;
        private KitScheduleRecord record;

        @JdbiConstructor
        public PendingScheduleRecord(
                @ColumnName("study_id") long studyId,
                @ColumnName("address_id") Long addressId,
                @ColumnName("address_validation_status") DsmAddressValidationStatus addressValidationStatus,
                @Nested KitScheduleRecord record) {
            this.studyId = studyId;
            this.addressId = addressId;
            this.addressValidationStatus = addressValidationStatus;
            this.record = record;
        }

        public long getStudyId() {
            return studyId;
        }

        public long getUserId() {
            return record.getUserId();
        }

        public String getUserGuid() {
            return record.getUserGuid();
        }

        public Long getAddressId() {
            return addressId;
        }

        public DsmAddressValidationStatus getAddressValidationStatus() {
            return addressValidationStatus;
        }

        public KitScheduleRecord getRecord() {
            return record;
        }
    }
}

package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
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

    default void updateRecordCurrentOccurrencePrepTime(long recordId, Instant currentOccurrencePrepTime) {
        DBUtils.checkUpdate(1, getKitScheduleSql().updateRecordCurrentOccurrencePrepTime(recordId, currentOccurrencePrepTime));
    }

    default void incrementRecordNumOccurrenceWithoutKit(long recordId) {
        DBUtils.checkUpdate(1, getKitScheduleSql().incrementRecordNumOccurrences(recordId, Instant.now(), null));
    }

    default void incrementRecordNumOccurrenceWithKit(long recordId, long kitRequestId) {
        DBUtils.checkUpdate(1, getKitScheduleSql().incrementRecordNumOccurrences(recordId, Instant.now(), kitRequestId));
    }

    @SqlQuery("select user.user_id,"
            + "       user.guid as user_guid,"
            + "       addr.address_id,"
            + "       vs.code as address_validation_status,"
            + "       usen.study_id,"
            + "       rec.*,"
            + "       (select kit_request_guid from kit_request where kit_request_id = rec.last_kit_request_id) as last_kit_request_guid"
            + "  from user_study_enrollment as usen"
            + "  join enrollment_status_type as entype on entype.enrollment_status_type_id = usen.enrollment_status_type_id"
            + "  join user on user.user_id = usen.user_id"
            + "  join kit_configuration as kc on kc.study_id = usen.study_id"
            + "  join kit_schedule_record as rec"
            + "       on rec.participant_user_id = user.user_id"
            + "       and rec.kit_configuration_id = kc.kit_configuration_id"
            + "  left join default_mailing_address as defaddr on defaddr.participant_user_id = user.user_id"
            + "  left join mailing_address as addr on addr.address_id = defaddr.address_id"
            + "  left join mailing_address_validation_status as vs on vs.mailing_address_validation_status_id = addr.validation_status_id"
            + " where entype.enrollment_status_type_code = 'ENROLLED'"
            + "   and valid_to is null"
            + "   and rec.kit_configuration_id = :configId"
            + "   and rec.num_occurrences < :maxOccurrences")
    @RegisterConstructorMapper(PendingScheduleRecord.class)
    @RegisterColumnMapperFactory(EnumByOrdinalMapperFactory.class)
    Stream<PendingScheduleRecord> findPendingScheduleRecords(
            @Bind("configId") long kitConfigurationId,
            @Bind("maxOccurrences") int maxOccurrences);

    class PendingScheduleRecord {
        private long studyId;
        private long userId;
        private String userGuid;
        private Long addressId;
        private DsmAddressValidationStatus addressValidationStatus;
        private KitScheduleRecord record;

        @JdbiConstructor
        public PendingScheduleRecord(
                @ColumnName("study_id") long studyId,
                @ColumnName("user_id") long userId,
                @ColumnName("user_guid") String userGuid,
                @ColumnName("address_id") Long addressId,
                @ColumnName("address_validation_status") DsmAddressValidationStatus addressValidationStatus,
                @Nested KitScheduleRecord record) {
            this.studyId = studyId;
            this.userId = userId;
            this.userGuid = userGuid;
            this.addressId = addressId;
            this.addressValidationStatus = addressValidationStatus;
            this.record = record;
        }

        public long getStudyId() {
            return studyId;
        }

        public long getUserId() {
            return userId;
        }

        public String getUserGuid() {
            return userGuid;
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

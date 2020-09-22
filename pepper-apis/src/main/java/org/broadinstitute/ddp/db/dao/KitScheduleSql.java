package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface KitScheduleSql extends SqlObject {

    //
    // Kit schedule
    //

    @SqlUpdate("insert into kit_schedule (kit_configuration_id, num_occurrences_per_user,"
            + "        next_time_amount, next_prep_time_amount, opt_out_expression, individual_opt_out_expression)"
            + " values (:configId, :num, :next, :nextPrep, :optOutExpr, :individualOptOutExpr)")
    int insertSchedule(
            @Bind("configId") long kitConfigurationId,
            @Bind("num") int numOccurrencesPerUser,
            @Bind("next") String nextTimeAmount,
            @Bind("nextPrep") String nextPrepTimeAmount,
            @Bind("optOutExpr") String optOutExpression,
            @Bind("individualOptOutExpr") String individualOptOutExpr);

    @SqlUpdate("delete from kit_schedule where kit_configuration_id = :configId")
    int deleteSchedule(@Bind("configId") long kitConfigurationId);

    //
    // Schedule record
    //

    @GetGeneratedKeys
    @SqlUpdate("insert into kit_schedule_record (participant_user_id, kit_configuration_id, opted_out, num_occurrences,"
            + "        initial_kit_request_id, last_occurrence_time, current_occurrence_prep_time)"
            + " values (:userId, :configId, :optedOut, :numOccurrences, :initialKitRequestId,"
            + "        :lastOccurrenceTime, :currentOccurrencePrepTime)")
    long insertRecord(
            @Bind("userId") long userId,
            @Bind("configId") long kitConfigurationId,
            @Bind("optedOut") boolean optedOut,
            @Bind("numOccurrences") int numOccurrences,
            @Bind("initialKitRequestId") long initialKitRequestId,
            @Bind("lastOccurrenceTime") Instant lastOccurrenceTime,
            @Bind("currentOccurrencePrepTime") Instant currentOccurrencePrepTime);

    @SqlUpdate("update kit_schedule_record"
            + "    set opted_out = :optedOut,"
            + "        num_occurrences = :numOccurrences,"
            + "        initial_kit_request_id = :initialKitRequestId,"
            + "        last_occurrence_time = :lastOccurrenceTime,"
            + "        current_occurrence_prep_time = :currentOccurrencePrepTime"
            + "  where kit_schedule_record_id = :id")
    int updateRecord(
            @Bind("id") long recordId,
            @Bind("optedOut") boolean optedOut,
            @Bind("numOccurrences") int numOccurrences,
            @Bind("initialKitRequestId") long initialKitRequestId,
            @Bind("lastOccurrenceTime") Instant lastOccurrenceTime,
            @Bind("currentOccurrencePrepTime") Instant currentOccurrencePrepTime);

    @SqlUpdate("update kit_schedule_record set opted_out = :optedOut where kit_schedule_record_id = :id")
    int updateRecordOptOut(@Bind("id") long recordId, @Bind("optedOut") boolean optedOut);

    @Deprecated
    @SqlUpdate("update kit_schedule_record set initial_kit_sent_time = :time where kit_schedule_record_id = :id")
    int updateRecordInitialKitSentTime(@Bind("id") long recordId, @Bind("time") Instant sentTime);

    @Deprecated
    @SqlBatch("update kit_schedule_record set initial_kit_sent_time = :time where kit_schedule_record_id = :id")
    int[] bulkUpdateRecordInitialKitSentTime(@Bind("id") List<Long> ids, @Bind("time") List<Instant> sentTimes);

    @SqlUpdate("update kit_schedule_record set current_occurrence_prep_time = :time where kit_schedule_record_id = :id")
    int updateRecordCurrentOccurrencePrepTime(@Bind("id") long recordId, @Bind("time") Instant currentOccurrencePrepTime);

    @SqlUpdate("update kit_schedule_record"
            + "    set num_occurrences = num_occurrences + 1,"
            + "        last_occurrence_time = :time,"
            + "        current_occurrence_prep_time = null"
            + "  where kit_schedule_record_id = :id")
    int incrementRecordNumOccurrences(
            @Bind("id") long recordId,
            @Bind("time") Instant lastOccurrenceTime);
}

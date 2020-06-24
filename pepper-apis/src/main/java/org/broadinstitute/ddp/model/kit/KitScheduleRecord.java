package org.broadinstitute.ddp.model.kit;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class KitScheduleRecord {

    private long id;
    private long userId;
    private long configId;
    private boolean optedOut;
    private int numOccurrences;
    private Instant lastOccurrenceTime;
    private Long lastKitRequestId;
    private String lastKitRequestGuid;
    private Instant lastKitSentTime;
    private Instant currentOccurrencePrepTime;

    @JdbiConstructor
    public KitScheduleRecord(
            @ColumnName("kit_schedule_record_id") long id,
            @ColumnName("participant_user_id") long userId,
            @ColumnName("kit_configuration_id") long configId,
            @ColumnName("opted_out") boolean optedOut,
            @ColumnName("num_occurrences") int numOccurrences,
            @ColumnName("last_occurrences_time") Instant lastOccurrenceTime,
            @ColumnName("last_kit_request_id") Long lastKitRequestId,
            @ColumnName("last_kit_request_guid") String lastKitRequestGuid,
            @ColumnName("last_kit_sent_time") Instant lastKitSentTime,
            @ColumnName("current_occurrence_prep_time") Instant currentOccurrencePrepTime) {
        this.id = id;
        this.userId = userId;
        this.configId = configId;
        this.optedOut = optedOut;
        this.numOccurrences = numOccurrences;
        this.lastOccurrenceTime = lastOccurrenceTime;
        this.lastKitRequestId = lastKitRequestId;
        this.lastKitRequestGuid = lastKitRequestGuid;
        this.lastKitSentTime = lastKitSentTime;
        this.currentOccurrencePrepTime = currentOccurrencePrepTime;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public long getConfigId() {
        return configId;
    }

    public boolean hasOptedOut() {
        return optedOut;
    }

    public int getNumOccurrences() {
        return numOccurrences;
    }

    public Instant getLastOccurrenceTime() {
        return lastOccurrenceTime;
    }

    public Long getLastKitRequestId() {
        return lastKitRequestId;
    }

    public String getLastKitRequestGuid() {
        return lastKitRequestGuid;
    }

    public Instant getLastKitSentTime() {
        return lastKitSentTime;
    }

    public Instant getCurrentOccurrencePrepTime() {
        return currentOccurrencePrepTime;
    }
}

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
    private Instant initialKitSentTime;
    private Instant lastOccurrenceTime;
    private Instant currentOccurrencePrepTime;

    @JdbiConstructor
    public KitScheduleRecord(
            @ColumnName("kit_schedule_record_id") long id,
            @ColumnName("participant_user_id") long userId,
            @ColumnName("kit_configuration_id") long configId,
            @ColumnName("opted_out") boolean optedOut,
            @ColumnName("num_occurrences") int numOccurrences,
            @ColumnName("initial_kit_sent_time") Instant initialKitSentTime,
            @ColumnName("last_occurrence_time") Instant lastOccurrenceTime,
            @ColumnName("current_occurrence_prep_time") Instant currentOccurrencePrepTime) {
        this.id = id;
        this.userId = userId;
        this.configId = configId;
        this.optedOut = optedOut;
        this.numOccurrences = numOccurrences;
        this.initialKitSentTime = initialKitSentTime;
        this.lastOccurrenceTime = lastOccurrenceTime;
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

    public Instant getInitialKitSentTime() {
        return initialKitSentTime;
    }

    public Instant getLastOccurrenceTime() {
        return lastOccurrenceTime;
    }

    public Instant getCurrentOccurrencePrepTime() {
        return currentOccurrencePrepTime;
    }

    /**
     * Get last time point so we can schedule the next kit based on that.
     *
     * @return instant or null
     */
    public Instant determineLastTimePoint() {
        if (initialKitSentTime == null) {
            // Initial kit hasn't been sent yet, can't schedule next kits without that, so nothing to do yet.
            return null;
        } else if (lastOccurrenceTime == null) {
            // Initial kit is sent but don't have last occurrence time. This means this is the first occurrence,
            // so use initial kit sent time as anchor.
            return initialKitSentTime;
        } else {
            // Use the last occurrence time to schedule the next kit.
            return lastOccurrenceTime;
        }
    }

    /**
     * Given the next prep time point, should we do prep step for this record?
     *
     * @param nextPrepTime the next prep time point
     * @return true if it's time for prep step and we haven't done it yet
     */
    public boolean shouldPerformPrepStep(Instant nextPrepTime) {
        return currentOccurrencePrepTime == null && nextPrepTime.isBefore(Instant.now());
    }
}

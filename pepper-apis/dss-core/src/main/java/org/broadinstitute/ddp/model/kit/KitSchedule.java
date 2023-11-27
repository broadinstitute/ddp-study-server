package org.broadinstitute.ddp.model.kit;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;

import org.broadinstitute.ddp.util.DateTimeUtils;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class KitSchedule {

    private long configId;
    private int numOccurrencesPerUser;
    private String nextTimeAmount;
    private Period nextPeriod;
    private Duration nextDuration;
    private String nextPrepTimeAmount;
    private Period nextPrepPeriod;
    private Duration nextPrepDuration;
    private String optOutExpr;
    private String individualOptOutExpr;

    @JdbiConstructor
    public KitSchedule(
            @ColumnName("kit_configuration_id") long configId,
            @ColumnName("num_occurrences_per_user") int numOccurrencesPerUser,
            @ColumnName("next_time_amount") String nextTimeAmount,
            @ColumnName("next_prep_time_amount") String nextPrepTimeAmount,
            @ColumnName("opt_out_expression") String optOutExpr,
            @ColumnName("individual_opt_out_expression") String individualOptOutExpr) {
        this.configId = configId;
        this.numOccurrencesPerUser = numOccurrencesPerUser;
        this.nextTimeAmount = nextTimeAmount;
        this.nextPrepTimeAmount = nextPrepTimeAmount;
        this.optOutExpr = optOutExpr;
        this.individualOptOutExpr = individualOptOutExpr;
        splitTimeAmount();
    }

    private void splitTimeAmount() {
        nextPeriod = DateTimeUtils.parseTimeAmountPeriod(nextTimeAmount);
        nextDuration = DateTimeUtils.parseTimeAmountDuration(nextTimeAmount);
        if (nextPrepTimeAmount != null) {
            nextPrepPeriod = DateTimeUtils.parseTimeAmountPeriod(nextPrepTimeAmount);
            nextPrepDuration = DateTimeUtils.parseTimeAmountDuration(nextPrepTimeAmount);
        }
    }

    public long getConfigId() {
        return configId;
    }

    public int getNumOccurrencesPerUser() {
        return numOccurrencesPerUser;
    }

    public String getNextTimeAmount() {
        return nextTimeAmount;
    }

    public Period getNextTimeAmountPeriod() {
        return nextPeriod;
    }

    public Duration getNextTimeAmountDuration() {
        return nextDuration;
    }

    public Instant getNextTimePoint(Instant from) {
        return from.plus(nextPeriod).plus(nextDuration);
    }

    public String getNextPrepTimeAmount() {
        return nextPrepTimeAmount;
    }

    public Period getNextPrepTimeAmountPeriod() {
        return nextPrepPeriod;
    }

    public Duration getNextPrepTimeAmountDuration() {
        return nextPrepDuration;
    }

    public Instant getNextPrepTimePoint(Instant from) {
        if (nextPrepTimeAmount != null) {
            return from.plus(nextPrepPeriod).plus(nextPrepDuration);
        } else {
            return null;
        }
    }

    public String getOptOutExpr() {
        return optOutExpr;
    }

    public String getIndividualOptOutExpr() {
        return individualOptOutExpr;
    }
}

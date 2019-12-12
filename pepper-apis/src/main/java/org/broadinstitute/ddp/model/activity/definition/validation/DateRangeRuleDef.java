package org.broadinstitute.ddp.model.activity.definition.validation;

import java.time.LocalDate;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.types.RuleType;

public final class DateRangeRuleDef extends RuleDef {

    @SerializedName("startDate")
    private LocalDate startDate;

    @SerializedName("endDate")
    private LocalDate endDate;

    @SerializedName("useTodayAsEnd")
    private boolean useTodayAsEnd;

    /**
     * Creates a definition of a date range validation rule.
     */
    public DateRangeRuleDef(Template hintTemplate, LocalDate startDate, LocalDate endDate, boolean useTodayAsEnd) {
        super(RuleType.DATE_RANGE, hintTemplate);
        this.startDate = startDate;
        this.endDate = endDate;
        this.useTodayAsEnd = useTodayAsEnd;
        if (useTodayAsEnd && endDate != null) {
            throw new IllegalArgumentException("End date should not be set when useTodayAsEnd is true");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date should not be after end date");
        }
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean isUseTodayAsEnd() {
        return useTodayAsEnd;
    }
}

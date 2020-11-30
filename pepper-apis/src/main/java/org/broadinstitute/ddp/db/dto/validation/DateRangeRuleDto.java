package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;
import java.time.LocalDate;

import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class DateRangeRuleDto extends RuleDto implements Serializable {

    private LocalDate startDate;
    private LocalDate endDate;
    private boolean useTodayAsEnd;

    @JdbiConstructor
    public DateRangeRuleDto(
            @Nested RuleDto ruleDto,
            @ColumnName("start_date") LocalDate startDate,
            @ColumnName("end_date") LocalDate endDate,
            @ColumnName("use_today_as_end") boolean useTodayAsEnd) {
        super(ruleDto);
        this.startDate = startDate;
        this.endDate = endDate;
        this.useTodayAsEnd = useTodayAsEnd;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public boolean shouldUseTodayAsEnd() {
        return useTodayAsEnd;
    }
}

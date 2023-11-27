package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;
import java.time.LocalDate;

import lombok.Value;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
public class DateRangeRuleDto extends RuleDto implements Serializable {
    LocalDate startDate;
    LocalDate endDate;
    boolean useTodayAsEnd;

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
}

package org.broadinstitute.ddp.model.activity.instance.validation;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that indicates a given date must be within a certain inclusive date range.
 */
public class DateRangeRule extends Rule<DateAnswer> {

    @SerializedName("startDate")
    private LocalDate startDate;

    @SerializedName("endDate")
    private LocalDate endDate;

    public static DateRangeRule of(Long id, String message, String hint, boolean allowSave, LocalDate startDate, LocalDate endDate) {
        DateRangeRule rule = new DateRangeRule(message, hint, allowSave, startDate, endDate);
        rule.setId(id);
        return rule;
    }

    public static DateRangeRule of(String message, String hint, boolean allowSave, LocalDate startDate, LocalDate endDate) {
        return new DateRangeRule(message, hint, allowSave, startDate, endDate);
    }

    private DateRangeRule(String message, String hint, boolean allowSave, LocalDate startDate, LocalDate endDate) {
        super(RuleType.DATE_RANGE, message, hint, allowSave);
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    @Override
    public boolean validate(Question<DateAnswer> question, DateAnswer answer) {
        if (answer != null && answer.getValue() != null) {
            if (!((DateQuestion) question).isSpecifiedFieldsPresent(answer)) {
                // Don't run range check unless specified fields are in answer. We might be in the middle of auto-save
                // where we only have a partial answer. Also, since these fields are not required, we don't try to
                // force it to be present. If they're needed, the RequiredRule should take care of that.
                return true;
            }
            return isWithinRange(answer.getValue());
        }
        return false;
    }

    private boolean isWithinRange(DateValue value) {
        if (value.isFullDate()) {
            return value.asLocalDate().map(this::isFullDateWithinRange).orElse(false);
        } else if (value.isYearMonth()) {
            return value.asYearMonth().map(this::isYearMonthWithinRange).orElse(false);
        } else if (value.isMonthDay()) {
            return value.asMonthDay().map(this::isMonthDayWithinRange).orElse(false);
        }

        Integer startYear = null;
        Integer startMonth = null;
        Integer startDay = null;
        if (startDate != null) {
            startYear = startDate.getYear();
            startMonth = startDate.getMonthValue();
            startDay = startDate.getDayOfMonth();
        }

        Integer endYear = null;
        Integer endMonth = null;
        Integer endDay = null;
        if (endDate != null) {
            endYear = endDate.getYear();
            endMonth = endDate.getMonthValue();
            endDay = endDate.getDayOfMonth();
        }

        if (value.isYearDay()) {
            return isYearDayWithinRange(startYear, startDay, value.getYear(), value.getDay(), endYear, endDay);
        } else if (value.getYear() != null) {
            return isFieldWithinRange(startYear, value.getYear(), endYear);
        } else if (value.getMonth() != null) {
            return isFieldWithinRange(startMonth, value.getMonth(), endMonth);
        } else if (value.getDay() != null) {
            return isFieldWithinRange(startDay, value.getDay(), endDay);
        } else {
            // No fields to look at, let it pass through.
            return true;
        }
    }

    private boolean isFullDateWithinRange(LocalDate value) {
        return (startDate == null || startDate.isEqual(value) || startDate.isBefore(value))
                && (endDate == null || value.isEqual(endDate) || value.isBefore(endDate));
    }

    private boolean isYearMonthWithinRange(YearMonth value) {
        YearMonth start = (startDate == null ? null : YearMonth.of(startDate.getYear(), startDate.getMonth()));
        YearMonth end = (endDate == null ? null : YearMonth.of(endDate.getYear(), endDate.getMonth()));
        return (start == null || start.equals(value) || start.isBefore(value))
                && (end == null || value.equals(end) || value.isBefore(end));
    }

    private boolean isMonthDayWithinRange(MonthDay value) {
        MonthDay start = (startDate == null ? null : MonthDay.of(startDate.getMonth(), startDate.getDayOfMonth()));
        MonthDay end = (endDate == null ? null : MonthDay.of(endDate.getMonth(), endDate.getDayOfMonth()));
        return (start == null || start.equals(value) || start.isBefore(value))
                && (end == null || value.equals(end) || value.isBefore(end));
    }

    private boolean isYearDayWithinRange(Integer startYear, Integer startDay, int year, int day, Integer endYear, Integer endDay) {
        return (startYear == null || startYear < year || (startYear == year && startDay <= day))
                && (endYear == null || year < endYear || (year == endYear && day <= endDay));
    }

    private boolean isFieldWithinRange(Integer start, int value, Integer end) {
        return (start == null || start <= value) && (end == null || value <= end);
    }
}

package org.broadinstitute.ddp.model.activity.instance.validation;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.question.DateQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that indicates a given date must indicate a minimum, maximum or range of ages based on today's date.
 */
public class AgeRangeRule extends Rule<DateAnswer> {

    @SerializedName("minAge")
    private Integer minAge;

    @SerializedName("maxAge")
    private Integer maxAge;

    private transient LocalDate today = null;

    public static AgeRangeRule of(Long id, String message, String hint, boolean allowSave, Integer minAge, Integer maxAge) {
        AgeRangeRule rule = new AgeRangeRule(message, hint, allowSave, minAge, maxAge);
        rule.setId(id);
        return rule;
    }

    public static AgeRangeRule of(String message, String hint, boolean allowSave, Integer minAge, Integer maxAge) {
        return new AgeRangeRule(message, hint, allowSave, minAge, maxAge);
    }

    private AgeRangeRule(String message, String hint, boolean allowSave, Integer minAge, Integer maxAge) {
        super(RuleType.AGE_RANGE, message, hint, allowSave);
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    public Integer getMinAge() {
        return minAge;
    }

    public Integer getMaxAge() {
        return maxAge;
    }

    @Override
    public boolean validate(Question<DateAnswer> question, DateAnswer answer) {
        if (answer != null && answer.getValue() != null) {
            if (!((DateQuestion) question).isSpecifiedFieldsPresent(answer)) {
                return true;
            }
            return isDateWithinAgeRange(answer.getValue());
        }
        return false;
    }

    private boolean isDateWithinAgeRange(DateValue value) {
        if (value.isFullDate()) {
            return value.asLocalDate().map(this::isFullDateWithinRange).orElse(false);
        } else {
            throw new DDPException("Only full dates are currently supported");
        }
    }

    private boolean isFullDateWithinRange(LocalDate value) {
        if (minAge != null) {
            if (ageAtDate(value) < minAge) {
                return false;
            }
        }
        if (maxAge != null) {
            return ageAtDate(value) <= maxAge;
        }

        return true;

    }

    private long ageAtDate(LocalDate birthDate) {
        // in case you are wondering: if born on February 29 in a leap year, your birthday in a non-leap year
        // is March 1st
        return ChronoUnit.YEARS.between(birthDate, getToday());
    }

    protected LocalDate getToday() {
        if (today == null) {
            return LocalDate.now();
        } else {
            return today;
        }
    }

    /**
     * Allow setting of date to be used as "today" to calculate ages.
     * At least initially, purpose is repeatable tests
     * @param testTodayDate date to be used to calculate ages
     */
    protected void setToday(LocalDate testTodayDate) {
        this.today = testTodayDate;
    }

}

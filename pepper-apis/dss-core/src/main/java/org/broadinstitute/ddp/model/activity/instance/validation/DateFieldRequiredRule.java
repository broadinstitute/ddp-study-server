package org.broadinstitute.ddp.model.activity.instance.validation;

import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.RuleType;

/**
 * A validation rule that indicates a certain date field is required.
 */
public class DateFieldRequiredRule extends Rule<DateAnswer> {

    /**
     * Return DateFieldRequiredRule with id based on given parameters.
     */
    public static DateFieldRequiredRule of(RuleType type, Long id, String message, String hint, boolean allowSave) {
        DateFieldRequiredRule rule = DateFieldRequiredRule.of(type, message, hint, allowSave);
        rule.setId(id);
        return rule;
    }

    /**
     * Return DateFieldRequiredRule based on given parameters.
     */
    public static DateFieldRequiredRule of(RuleType type, String message, String hint, boolean allowSave) {
        checkType(type);
        return new DateFieldRequiredRule(type, message, hint, allowSave);
    }

    private static void checkType(RuleType type) {
        if (!RuleType.YEAR_REQUIRED.equals(type)
                && !RuleType.MONTH_REQUIRED.equals(type)
                && !RuleType.DAY_REQUIRED.equals(type)) {
            throw new IllegalArgumentException("Unknown date field required rule type " + type);
        }
    }

    private DateFieldRequiredRule(RuleType type, String message, String hint, boolean allowSave) {
        super(type, message, hint, allowSave);
    }

    @Override
    public boolean validate(Question<DateAnswer> question, DateAnswer answer) {
        if (answer != null && answer.getValue() != null && !answer.getValue().isBlank()) {
            DateValue value = answer.getValue();
            if (type.equals(RuleType.DAY_REQUIRED)) {
                return value.getDay() != null;
            } else if (type.equals(RuleType.MONTH_REQUIRED)) {
                return value.getMonth() != null;
            } else if (type.equals(RuleType.YEAR_REQUIRED)) {
                return value.getYear() != null;
            }
        }
        return true;
    }
}

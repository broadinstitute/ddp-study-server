package org.broadinstitute.ddp.model.activity.types;

public enum RuleType {
    AGE_RANGE,
    COMPLETE,
    DATE_RANGE,
    DAY_REQUIRED,
    INT_RANGE,
    DECIMAL_RANGE,
    COMPARISON,
    LENGTH,
    MONTH_REQUIRED,
    NUM_OPTIONS_SELECTED,
    REGEX,
    REQUIRED,
    UNIQUE, //check for uniqueness in answers of a composite question
    UNIQUE_VALUE, //check for uniqueness in same question answers. Scope: All participants of the study
    YEAR_REQUIRED
}

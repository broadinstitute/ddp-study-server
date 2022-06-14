package org.broadinstitute.ddp.model.governance;

import java.time.LocalDate;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents rule that helps determine if participant has reached the moment of age-of-majority, and for calculating that date.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class AgeOfMajorityRule {
    @ColumnName("age_of_majority_rule_id")
    long id;

    @ColumnName("study_governance_policy_id")
    long policyId;

    @ColumnName("condition_expression")
    String condition;

    @ColumnName("age")
    int age;

    @ColumnName("preparation_months")
    Integer prepMonths;

    @ColumnName("execution_order")
    int order;

    public AgeOfMajorityRule(String condition, int age, Integer prepMonths) {
        this(0, 0, condition, age, prepMonths, 0);
    }

    public LocalDate getDateOfMajority(LocalDate birthDate) {
        return birthDate.plusYears(age);
    }

    public Optional<LocalDate> getMajorityPrepDate(LocalDate birthDate) {
        return Optional.ofNullable(prepMonths).map(months -> getDateOfMajority(birthDate).minusMonths(months));
    }

    public boolean hasReachedAgeOfMajority(LocalDate birthDate, LocalDate today) {
        return getDateOfMajority(birthDate).compareTo(today) <= 0;
    }

    public Optional<Boolean> hasReachedAgeOfMajorityPrep(LocalDate birthDate, LocalDate today) {
        return getMajorityPrepDate(birthDate).map(prepDate -> prepDate.compareTo(today) <= 0);
    }
}

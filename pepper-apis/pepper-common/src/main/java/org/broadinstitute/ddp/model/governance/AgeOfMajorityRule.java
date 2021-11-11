package org.broadinstitute.ddp.model.governance;

import java.time.LocalDate;
import java.util.Optional;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents rule that helps determine if participant has reached the moment of age-of-majority, and for calculating that date.
 */
public class AgeOfMajorityRule {

    private long id;
    private long policyId;
    private String condition;
    private int age;
    private Integer prepMonths;
    private int order;

    @JdbiConstructor
    public AgeOfMajorityRule(@ColumnName("age_of_majority_rule_id") long id,
                             @ColumnName("study_governance_policy_id") long policyId,
                             @ColumnName("condition_expression") String condition,
                             @ColumnName("age") int age,
                             @ColumnName("preparation_months") Integer prepMonths,
                             @ColumnName("execution_order") int order) {
        this.id = id;
        this.policyId = policyId;
        this.condition = condition;
        this.age = age;
        this.prepMonths = prepMonths;
        this.order = order;
    }

    public AgeOfMajorityRule(String condition, int age, Integer prepMonths) {
        this.condition = condition;
        this.age = age;
        this.prepMonths = prepMonths;
    }

    public long getId() {
        return id;
    }

    public long getPolicyId() {
        return policyId;
    }

    public String getCondition() {
        return condition;
    }

    public int getAge() {
        return age;
    }

    public Integer getPrepMonths() {
        return prepMonths;
    }

    public int getOrder() {
        return order;
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

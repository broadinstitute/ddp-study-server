package org.broadinstitute.ddp.equation;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluatorBuilder {
    private final Map<String, BigDecimal> variables = new HashMap<>();

    public EquationEvaluatorBuilder withVariableValue(final String variable, final BigDecimal value) {
        variables.put(variable, value);
        return this;
    }

    public EquationEvaluatorBuilder withVariablesValues(final Map<String, BigDecimal> values) {
        variables.putAll(values);
        return this;
    }

    public EquationEvaluator build() {
        return new EquationEvaluator(variables);
    }
}

package org.broadinstitute.ddp.equation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluatorBuilder {
    private final Map<String, BigDecimal> variables = new HashMap<>();

    public EquationEvaluatorBuilder withVariableValue(final String variable, final BigDecimal value) {
        variables.put(variable, value);
        return this;
    }

    public EquationEvaluator build() {
        return new EquationEvaluator(variables);
    }
}

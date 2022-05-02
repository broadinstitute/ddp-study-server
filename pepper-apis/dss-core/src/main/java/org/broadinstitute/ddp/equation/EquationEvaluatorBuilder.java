package org.broadinstitute.ddp.equation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import one.util.streamex.EntryStream;
import org.broadinstitute.ddp.equation.containers.AbstractVector;
import org.broadinstitute.ddp.equation.containers.MultiValueVector;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluatorBuilder {
    private final Map<String, List<BigDecimal>> variables = new HashMap<>();

    public EquationEvaluatorBuilder withVariableValues(final String variable, final List<BigDecimal> value) {
        variables.put(variable, value);
        return this;
    }

    public EquationEvaluatorBuilder withVariablesValues(final Map<String, List<BigDecimal>> values) {
        variables.putAll(values);
        return this;
    }

    public EquationEvaluator build() {
        return new EquationEvaluator(EntryStream.of(variables).mapValues(this::toAbstractVector).toMap());
    }

    private AbstractVector toAbstractVector(final List<BigDecimal> values) {
        return new MultiValueVector(values);
    }
}

package org.broadinstitute.ddp.equation.containers;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode
@AllArgsConstructor
public final class SingleValueVector implements AbstractVector {
    private final BigDecimal value;

    @Override
    public BigDecimal get(int index) {
        return value;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public List<BigDecimal> toList() {
        return Collections.singletonList(value);
    }
}

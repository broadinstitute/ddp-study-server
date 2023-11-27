package org.broadinstitute.ddp.equation.containers;


import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode
@AllArgsConstructor
public final class MultiValueVector implements AbstractVector {
    private final List<BigDecimal> components;

    @Override
    public BigDecimal get(int index) {
        return components.get(index);
    }

    @Override
    public int size() {
        return components.size();
    }

    @Override
    public List<BigDecimal> toList() {
        return new ArrayList<>(components);
    }
}

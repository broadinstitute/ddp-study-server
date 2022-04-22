package org.broadinstitute.ddp.equation.containers;

import java.math.BigDecimal;
import java.util.List;

public interface AbstractVector {
    BigDecimal get(final int index);

    int size();

    List<BigDecimal> toList();
}

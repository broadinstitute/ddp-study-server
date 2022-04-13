package org.broadinstitute.ddp.equation;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.ArrayList;

@Value
@AllArgsConstructor
public class SingleValueList extends ArrayList<BigDecimal> {
    BigDecimal value;

    @Override
    public BigDecimal get(int index) {
        return value;
    }

    @Override
    public int size() {
        return 1;
    }
}

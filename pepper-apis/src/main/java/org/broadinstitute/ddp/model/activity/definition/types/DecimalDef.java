package org.broadinstitute.ddp.model.activity.definition.types;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.math.BigInteger;

@Value
@AllArgsConstructor
public class DecimalDef implements Comparable<DecimalDef> {
    BigInteger value;
    int scale;

    public DecimalDef(final BigDecimal decimal) {
        this(decimal.unscaledValue(), decimal.scale());
    }

    public DecimalDef(final int integer) {
        this(BigDecimal.valueOf(integer));
    }

    @Override
    public int compareTo(final DecimalDef o) {
        return toBigDecimal().compareTo(o.toBigDecimal());
    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(value, scale);
    }
}

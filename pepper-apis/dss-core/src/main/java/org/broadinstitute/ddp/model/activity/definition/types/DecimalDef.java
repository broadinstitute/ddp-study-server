package org.broadinstitute.ddp.model.activity.definition.types;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.math.BigInteger;

@Value
@AllArgsConstructor
public class DecimalDef implements Comparable<DecimalDef> {
    @SerializedName("value")
    BigInteger value;

    @SerializedName("scale")
    int scale;

    public DecimalDef(final BigDecimal decimal) {
        this(decimal.unscaledValue(), decimal.scale());
    }

    public DecimalDef(final int integer) {
        this(BigDecimal.valueOf(integer));
    }

    public DecimalDef(final String value) {
        this(new BigDecimal(value));
    }

    @Override
    public int compareTo(final DecimalDef o) {
        return toBigDecimal().compareTo(o.toBigDecimal());
    }

    public int compareTo(final BigDecimal o) {
        return toBigDecimal().compareTo(o);
    }

    public BigDecimal toBigDecimal() {
        return new BigDecimal(value, scale);
    }
}

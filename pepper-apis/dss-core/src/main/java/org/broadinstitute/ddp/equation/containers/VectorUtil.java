package org.broadinstitute.ddp.equation.containers;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import one.util.streamex.IntStreamEx;

import java.math.BigDecimal;
import java.util.function.BiFunction;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class VectorUtil {
    public static AbstractVector apply(final AbstractVector left, final AbstractVector right,
                                       final BiFunction<BigDecimal, BigDecimal, BigDecimal> componentFunction) {

        if (!areCompatible(left, right)) {
            throw new RuntimeException("Can't evaluate equation. The arguments have different sizes");
        }

        return new MultiValueVector(IntStreamEx.range(0, Math.max(left.size(), right.size()))
                .mapToObj(index -> apply(left.get(index), right.get(index), componentFunction))
                .toList());
    }

    private static BigDecimal apply(final BigDecimal left, final BigDecimal right,
                                    final BiFunction<BigDecimal, BigDecimal, BigDecimal> componentFunctional) {
        if (left == null || right == null) {
            return null;
        }

        return componentFunctional.apply(left, right);
    }

    private static boolean areCompatible(final AbstractVector left, final AbstractVector right) {
        return left.size() == 1 || right.size() == 1 || left.size() == right.size();
    }
}

package org.broadinstitute.ddp.equation.compute;

import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.broadinstitute.ddp.equation.EquationEvaluator;
import org.broadinstitute.ddp.equation.containers.AbstractVector;
import org.broadinstitute.ddp.equation.containers.MultiValueVector;
import org.broadinstitute.ddp.equation.containers.SingleValueVector;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class EquationEvaluationTest {
    @Test(expected = ParseCancellationException.class)
    public void testEvaluateEmpty() {
        evaluate("");
    }

    @Test
    public void testEvaluateConstant() {
        assertEquals(expected("3.14"), evaluate("3.14"));
        assertEquals(expected("-3.14"), evaluate("-3.14"));
    }

    @Test
    public void testEvaluateConstantWithParenthesis() {
        assertEquals(expected("3.14"), evaluate("(3.14)"));
        assertEquals(expected("-3.14"), evaluate("(-3.14)"));
    }

    @Test
    public void testEvaluateAddition() {
        assertEquals(expected("5.85"), evaluate("3.14 + 2.71"));
        assertEquals(expected("5.85"), evaluate("(3.14 + 2.71)"));
        assertEquals(evaluate("3.14 + 2.71"), evaluate("2.71 + 3.14"));
    }

    @Test
    public void testEvaluateSubtraction() {
        assertEquals(expected("0.43"), evaluate("3.14 - 2.71"));
        assertEquals(expected("0.43"), evaluate("(3.14 - 2.71)"));

        assertEquals(expected("-0.43"), evaluate("2.71 - 3.14"));
        assertEquals(expected("-0.43"), evaluate("(2.71 - 3.14)"));
    }

    @Test
    public void testEvaluateMultiplication() {
        assertEquals(expected("8.5094"), evaluate("3.14 * 2.71"));
        assertEquals(expected("8.5094"), evaluate("(3.14 * 2.71)"));
        assertEquals(evaluate("3.14 * 2.71"), evaluate("2.71 * 3.14"));
    }

    @Test
    public void testEvaluateDivision() {
        assertEquals(expected("1.158671586715867"), evaluate("3.14 / 2.71"));
        assertEquals(expected("1.158671586715867"), evaluate("(3.14 / 2.71)"));

        assertEquals(expected("0.8630573248407643"), evaluate("2.71 / 3.14"));
        assertEquals(expected("0.8630573248407643"), evaluate("(2.71 / 3.14)"));
    }

    @Test(expected = ArithmeticException.class)
    public void testEvaluateDivisionByZero() {
        evaluate("2 / 0");
    }

    @Test
    public void testEvaluatePower() {
        assertEquals(expected("22.21668954600232"), evaluate("3.14 ^ 2.71"));
        assertEquals(expected("22.21668954600232"), evaluate("(3.14 ^ 2.71)"));

        assertEquals(expected("0.04369949585003313"), evaluate("2.71 ^ -3.14"));
    }

    @Test
    public void testEvaluateWithVariables() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValues("x", Collections.singletonList(BigDecimal.ONE))
                .withVariableValues("y", Collections.singletonList(BigDecimal.valueOf(2.0)))
                .build();

        assertEquals(expected("16.69"), evaluator.evaluate("3.14 + 2.71 * (x + y ^ 2)"));
    }

    @Test
    public void testEvaluateVarious() {
        assertEquals(expected(BigDecimal.TEN), evaluate("(2 + 3) * 2"));
        assertEquals(expected(BigDecimal.ONE), evaluate("(2 + 3) ^ 0"));
        assertEquals(expected(BigDecimal.ZERO), evaluate("(2 + 3) ^ 2 - 625 / 25"));
        assertEquals(expected(BigDecimal.TEN), evaluate("(2 + 3) * (5 - 3)"));
    }

    @Test
    public void testEvaluateWithVector() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValues("x", Arrays.asList(BigDecimal.ZERO, null, BigDecimal.valueOf(5)))
                .build();

        assertEquals(expected(Arrays.asList(BigDecimal.ZERO, null, BigDecimal.TEN)),
                evaluator.evaluate("x * 2.0"));
    }

    @Test
    public void testEvaluateWithTwoVectors() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValues("x", Arrays.asList(BigDecimal.ZERO, null, BigDecimal.ONE))
                .withVariableValues("y", Arrays.asList(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN))
                .build();

        assertEquals(expected(Arrays.asList(BigDecimal.ZERO, null, BigDecimal.TEN)),
                evaluator.evaluate("x * y"));
    }

    @Test(expected = RuntimeException.class)
    public void testEvaluateWithVectorsOfIncompatibleSize() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValues("x", Arrays.asList(BigDecimal.ZERO, BigDecimal.ONE))
                .withVariableValues("y", Arrays.asList(BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN))
                .build();

        evaluator.evaluate("x * y");
    }

    private static void assertEquals(final AbstractVector expected, final AbstractVector actual) {
        Assert.assertEquals(expected.size(), actual.size());

        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i), actual.get(i));
        }
    }

    private static void assertEquals(final BigDecimal expected, final BigDecimal actual) {
        if (expected == null && actual == null) {
            return;
        }

        Assert.assertNotNull(actual);
        Assert.assertNotNull(expected);
        Assert.assertEquals(0, expected.compareTo(actual));
    }

    private AbstractVector evaluate(final String expression) {
        return EquationEvaluator.builder().build().evaluate(expression);
    }

    private AbstractVector expected(final String value) {
        return expected(new BigDecimal(value));
    }

    private AbstractVector expected(final BigDecimal value) {
        return new SingleValueVector(value);
    }

    private AbstractVector expected(final List<BigDecimal> values) {
        return new MultiValueVector(values);
    }
}

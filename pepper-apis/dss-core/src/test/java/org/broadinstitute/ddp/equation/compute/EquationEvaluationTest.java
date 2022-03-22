package org.broadinstitute.ddp.equation.compute;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.broadinstitute.ddp.equation.EquationEvaluator;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;
import org.junit.Assert;
import org.junit.Test;

import java.math.BigDecimal;

public class EquationEvaluationTest {
    @Test(expected = ParseCancellationException.class)
    public void testEvaluateEmpty() {
        evaluate("");
    }

    @Test
    public void testEvaluateConstant() {
        assertEquals(new BigDecimal("3.14"), evaluate("3.14"));
        assertEquals(new BigDecimal("-3.14"), evaluate("-3.14"));
    }

    @Test
    public void testEvaluateConstantWithParenthesis() {
        assertEquals(new BigDecimal("3.14"), evaluate("(3.14)"));
        assertEquals(new BigDecimal("-3.14"), evaluate("(-3.14)"));
    }

    @Test
    public void testEvaluateAddition() {
        assertEquals(new BigDecimal("5.85"), evaluate("3.14 + 2.71"));
        assertEquals(new BigDecimal("5.85"), evaluate("(3.14 + 2.71)"));
        assertEquals(evaluate("3.14 + 2.71"), evaluate("2.71 + 3.14"));
    }

    @Test
    public void testEvaluateSubtraction() {
        assertEquals(new BigDecimal("0.43"), evaluate("3.14 - 2.71"));
        assertEquals(new BigDecimal("0.43"), evaluate("(3.14 - 2.71)"));

        assertEquals(new BigDecimal("-0.43"), evaluate("2.71 - 3.14"));
        assertEquals(new BigDecimal("-0.43"), evaluate("(2.71 - 3.14)"));
    }

    @Test
    public void testEvaluateMultiplication() {
        assertEquals(new BigDecimal("8.5094"), evaluate("3.14 * 2.71"));
        assertEquals(new BigDecimal("8.5094"), evaluate("(3.14 * 2.71)"));
        assertEquals(evaluate("3.14 * 2.71"), evaluate("2.71 * 3.14"));
    }

    @Test
    public void testEvaluateDivision() {
        assertEquals(new BigDecimal("1.158671586715867"), evaluate("3.14 / 2.71"));
        assertEquals(new BigDecimal("1.158671586715867"), evaluate("(3.14 / 2.71)"));

        assertEquals(new BigDecimal("0.8630573248407643"), evaluate("2.71 / 3.14"));
        assertEquals(new BigDecimal("0.8630573248407643"), evaluate("(2.71 / 3.14)"));
    }

    @Test(expected = ArithmeticException.class)
    public void testEvaluateDivisionByZero() {
        evaluate("2 / 0");
    }

    @Test
    public void testEvaluatePower() {
        assertEquals(new BigDecimal("22.21668954600232"), evaluate("3.14 ^ 2.71"));
        assertEquals(new BigDecimal("22.21668954600232"), evaluate("(3.14 ^ 2.71)"));

        assertEquals(new BigDecimal("0.04369949585003313"), evaluate("2.71 ^ -3.14"));
    }

    @Test
    public void testEvaluateWithVariables() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValue("x", BigDecimal.ONE)
                .withVariableValue("y", BigDecimal.valueOf(2.0))
                .build();

        assertEquals(new BigDecimal("16.69"),
                evaluator.visit(buildParser("3.14 + 2.71 * (x + y ^ 2)").expression()));
    }

    @Test
    public void testEvaluateVarious() {
        assertEquals(BigDecimal.TEN, evaluate("(2 + 3) * 2"));
        assertEquals(BigDecimal.ONE, evaluate("(2 + 3) ^ 0"));
        assertEquals(BigDecimal.ZERO, evaluate("(2 + 3) ^ 2 - 625 / 25"));
        assertEquals(BigDecimal.TEN, evaluate("(2 + 3) * (5 - 3)"));
    }

    private static void assertEquals(final BigDecimal expected, final BigDecimal actual) {
        Assert.assertEquals(0, expected.compareTo(actual));
    }

    private BigDecimal evaluate(final String expression) {
        return EquationEvaluator.builder().build().visit(buildParser(expression).expression());
    }

    private EquationLexer buildLexer(final String expression) {
        return new EquationLexer(CharStreams.fromString(expression));
    }

    private EquationParser buildParser(final String expression) {
        final EquationParser parser = new EquationParser(new CommonTokenStream(buildLexer(expression)));
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }
}

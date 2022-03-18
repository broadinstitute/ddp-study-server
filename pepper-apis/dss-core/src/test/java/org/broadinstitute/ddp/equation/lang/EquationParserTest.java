package org.broadinstitute.ddp.equation.lang;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EquationParserTest {
    private static final String EXPRESSION = "3.14 + 2.71 * (x + y ^ 2)";
    private static final String TREE = "(expression (expression (expression (number 3.14)) + "
            + "(expression (number 2.71))) * (expression ( (expression (expression (expression (variable x)) + "
            + "(expression (variable y))) ^ (expression (number 2))) )))";

    @Test
    public void testParsing_tree() {
        testParserTreeOutput(EXPRESSION, TREE);
    }

    @Test(expected = ParseCancellationException.class)
    public void testParsing_empty() {
        buildParser(" ").expression();
    }

    @Test
    public void testWhitespace_ignore() {
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "  "), TREE);
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "\t"), TREE);
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "\n"), TREE);
    }

    @Test
    public void testUnaryOperator() {
        testParserTreeOutput("x + -2", "(expression (expression (variable x)) + (expression (number -2)))");
    }

    @Test(expected = ParseCancellationException.class)
    public void testStartingWithBinaryOperator() {
        buildParser("+5").expression();
    }

    @Test(expected = ParseCancellationException.class)
    public void testTwoBinaryOperatorsInTheRow() {
        buildParser("X +/ y").expression();
    }

    private void testParserTreeOutput(final String expression, final String expected) {
        final EquationParser parser = buildParser(expression);
        assertEquals(expected, parser.expression().toStringTree(parser));
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

package org.broadinstitute.ddp.equation.lang;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EquationParserTest {
    private static final String EXPRESSION = "3.14 + 2.71 * (x + y ^ 2)";
    private static final String TREE = "(expression (expression (atom (number 3.14))) + " +
            "(expression (expression (atom (number 2.71))) * (expression ( (expression (expression (atom (variable x))) + " +
            "(expression (expression (atom (variable y))) ^ (expression (atom (number 2))))) ))))";

    @Test
    public void testParsing_tree() {
        assertNotNull(buildParser(EXPRESSION).expression());
    }

    @Test
    public void testParsing_empty() {
        testParserTreeOutput(" ", "expression");
    }

    @Test
    public void testWhitespace_ignore() {
        testParserTreeOutput(EXPRESSION, TREE);
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "  "), TREE);
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "\t"), TREE);
        testParserTreeOutput(EXPRESSION.replaceAll(" ", "\n"), TREE);
    }

    private void testParserTreeOutput(final String expression, final String expected) {
        final EquationParser parser = buildParser(expression);
        assertEquals(expected, parser.expression().toStringTree(parser));
    }

    private EquationLexer buildLexer(final String expression) {
        return new EquationLexer(CharStreams.fromString(expression));
    }

    private EquationParser buildParser(final String expression) {
        return new EquationParser(new CommonTokenStream(buildLexer(expression)));
    }
}

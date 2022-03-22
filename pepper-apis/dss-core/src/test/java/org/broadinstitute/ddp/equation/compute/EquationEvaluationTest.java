package org.broadinstitute.ddp.equation.compute;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.broadinstitute.ddp.equation.EquationEvaluator;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;
import org.junit.Test;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

public class EquationEvaluationTest {
    private static final String EXPRESSION = "3.14 + 2.71 * (x + y ^ 2)";

    @Test
    public void testParsing() {
        final EquationEvaluator evaluator = EquationEvaluator.builder()
                .withVariableValue("x", BigDecimal.ONE)
                .withVariableValue("y", BigDecimal.valueOf(2.0))
                .build();

        assertEquals(BigDecimal.ZERO, evaluator.visitExpression(buildParser(EXPRESSION).expression()));
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

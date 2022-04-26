package org.broadinstitute.ddp.equation;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.broadinstitute.ddp.equation.containers.AbstractVector;
import org.broadinstitute.ddp.equation.containers.SingleValueVector;
import org.broadinstitute.ddp.equation.containers.VectorUtil;
import org.broadinstitute.ddp.pex.lang.EquationBaseVisitor;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluator extends EquationBaseVisitor<AbstractVector> {
    private final Map<String, AbstractVector> variables;

    public static EquationEvaluatorBuilder builder() {
        return new EquationEvaluatorBuilder();
    }

    @Override
    public AbstractVector visitPower(final EquationParser.PowerContext ctx) {
        return VectorUtil.apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (base, power) -> BigDecimalMath.pow(base, power, MathContext.DECIMAL64));
    }

    @Override
    public AbstractVector visitMultiplication(final EquationParser.MultiplicationContext ctx) {
        return VectorUtil.apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (left, right) -> left.multiply(right, MathContext.DECIMAL64));
    }

    @Override
    public AbstractVector visitDivision(final EquationParser.DivisionContext ctx) {
        return VectorUtil.apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (dividend, divisor) -> dividend.divide(divisor, MathContext.DECIMAL64));
    }

    @Override
    public AbstractVector visitAddition(final EquationParser.AdditionContext ctx) {
        return VectorUtil.apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (left, right) -> left.add(right, MathContext.DECIMAL64));
    }

    @Override
    public AbstractVector visitSubtraction(final EquationParser.SubtractionContext ctx) {
        return VectorUtil.apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (minuend, subtrahend) -> minuend.subtract(subtrahend, MathContext.DECIMAL64));
    }

    @Override
    public AbstractVector visitParentheses(final EquationParser.ParenthesesContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public AbstractVector visitVariable(final EquationParser.VariableContext ctx) {
        if (!variables.containsKey(ctx.getText())) {
            throw new RuntimeException("Can't evaluate equation. Variable " + ctx.getText() + " doesn't have a value");
        }

        return variables.get(ctx.getText());
    }

    @Override
    public AbstractVector visitNumber(final EquationParser.NumberContext ctx) {
        return new SingleValueVector(new BigDecimal(ctx.getText()));
    }

    public AbstractVector evaluate(final String expression) {
        return visit(buildParser(expression).expression());
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

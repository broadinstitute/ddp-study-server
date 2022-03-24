package org.broadinstitute.ddp.equation;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.broadinstitute.ddp.pex.lang.EquationBaseVisitor;
import org.broadinstitute.ddp.pex.lang.EquationParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluator extends EquationBaseVisitor<BigDecimal> {
    private final Map<String, BigDecimal> variables;

    public static EquationEvaluatorBuilder builder() {
        return new EquationEvaluatorBuilder();
    }

    @Override
    public BigDecimal visitPower(final EquationParser.PowerContext ctx) {
        return BigDecimalMath.pow(visit(ctx.expression(0)), visit(ctx.expression(1)), MathContext.DECIMAL64);
    }

    @Override
    public BigDecimal visitMultiplication(final EquationParser.MultiplicationContext ctx) {
        return visit(ctx.expression(0)).multiply(visit(ctx.expression(1)), MathContext.DECIMAL64);
    }

    @Override
    public BigDecimal visitDivision(final EquationParser.DivisionContext ctx) {
        return visit(ctx.expression(0)).divide(visit(ctx.expression(1)), MathContext.DECIMAL64);
    }

    @Override
    public BigDecimal visitAddition(final EquationParser.AdditionContext ctx) {
        return visit(ctx.expression(0)).add(visit(ctx.expression(1)), MathContext.DECIMAL64);
    }

    @Override
    public BigDecimal visitSubtraction(final EquationParser.SubtractionContext ctx) {
        return visit(ctx.expression(0)).subtract(visit(ctx.expression(1)), MathContext.DECIMAL64);
    }

    @Override
    public BigDecimal visitParentheses(final EquationParser.ParenthesesContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public BigDecimal visitVariable(final EquationParser.VariableContext ctx) {
        if (!variables.containsKey(ctx.getText())) {
            throw new RuntimeException("Can't evaluate equation. Variable " + ctx.getText() + " doesn't have a value");
        }

        return variables.get(ctx.getText());
    }

    @Override
    public BigDecimal visitNumber(final EquationParser.NumberContext ctx) {
        return new BigDecimal(ctx.getText());
    }
}

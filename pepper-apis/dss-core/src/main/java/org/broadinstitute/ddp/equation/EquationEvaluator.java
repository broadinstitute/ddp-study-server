package org.broadinstitute.ddp.equation;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.broadinstitute.ddp.pex.lang.EquationBaseVisitor;
import org.broadinstitute.ddp.pex.lang.EquationParser;

import java.math.BigDecimal;
import java.util.Map;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluator extends EquationBaseVisitor<BigDecimal> {
    private final Map<String, BigDecimal> variables;

    public static EquationEvaluatorBuilder builder() {
        return new EquationEvaluatorBuilder();
    }

    @Override
    public BigDecimal visitFile_(final EquationParser.File_Context ctx) {
        return super.visitFile_(ctx);
    }

    @Override
    public BigDecimal visitExpression(final EquationParser.ExpressionContext ctx) {
        return super.visitExpression(ctx);
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

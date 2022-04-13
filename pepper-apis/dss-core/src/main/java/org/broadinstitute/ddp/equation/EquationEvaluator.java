package org.broadinstitute.ddp.equation;

import ch.obermuhlner.math.big.BigDecimalMath;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import one.util.streamex.IntStreamEx;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.broadinstitute.ddp.pex.lang.EquationBaseVisitor;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
public final class EquationEvaluator extends EquationBaseVisitor<List<BigDecimal>> {
    private final Map<String, List<BigDecimal>> variables;

    public static EquationEvaluatorBuilder builder() {
        return new EquationEvaluatorBuilder();
    }

    @Override
    public List<BigDecimal> visitPower(final EquationParser.PowerContext ctx) {
        return apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (base, power) -> BigDecimalMath.pow(base, power, MathContext.DECIMAL64));
    }

    @Override
    public List<BigDecimal> visitMultiplication(final EquationParser.MultiplicationContext ctx) {
        return apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (left, right) -> left.multiply(right, MathContext.DECIMAL64));
    }

    @Override
    public List<BigDecimal> visitDivision(final EquationParser.DivisionContext ctx) {
        return apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (dividend, divisor) -> dividend.divide(divisor, MathContext.DECIMAL64));
    }

    @Override
    public List<BigDecimal> visitAddition(final EquationParser.AdditionContext ctx) {
        return apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (left, right) -> left.add(right, MathContext.DECIMAL64));
    }

    @Override
    public List<BigDecimal> visitSubtraction(final EquationParser.SubtractionContext ctx) {
        return apply(visit(ctx.expression(0)), visit(ctx.expression(1)),
                (minuend, subtrahend) -> minuend.subtract(subtrahend, MathContext.DECIMAL64));
    }

    @Override
    public List<BigDecimal> visitParentheses(final EquationParser.ParenthesesContext ctx) {
        return visit(ctx.expression());
    }

    @Override
    public List<BigDecimal> visitVariable(final EquationParser.VariableContext ctx) {
        if (!variables.containsKey(ctx.getText())) {
            throw new RuntimeException("Can't evaluate equation. Variable " + ctx.getText() + " doesn't have a value");
        }

        return variables.get(ctx.getText());
    }

    @Override
    public List<BigDecimal> visitNumber(final EquationParser.NumberContext ctx) {
        return new SingleValueList(new BigDecimal(ctx.getText()));
    }

    public List<BigDecimal> evaluate(final String expression) {
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

    private List<BigDecimal> apply(final List<BigDecimal> left, final List<BigDecimal> right,
                                   final BiFunction<BigDecimal, BigDecimal, BigDecimal> componentFunction) {
        if (left instanceof SingleValueList) {
            return apply(IntStreamEx.range(0, right.size()).mapToObj(left::get).toList(), right, componentFunction);
        }

        if (right instanceof SingleValueList) {
            return apply(left, IntStreamEx.range(0, left.size()).mapToObj(right::get).toList(), componentFunction);
        }

        if (left.size() != right.size()) {
            throw new RuntimeException("Can't evaluate equation. The arguments have different sizes");
        }

        return IntStreamEx.range(0, left.size())
                .mapToObj(i -> apply(left.get(i), right.get(i), componentFunction))
                .toList();
    }

    private BigDecimal apply(final BigDecimal left, final BigDecimal right,
                             final BiFunction<BigDecimal, BigDecimal, BigDecimal> componentFunctional) {
        if (left == null || right == null) {
            return null;
        }

        return componentFunctional.apply(left, right);
    }
}

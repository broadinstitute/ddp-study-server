package org.broadinstitute.ddp.equation;

import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.broadinstitute.ddp.pex.lang.EquationBaseVisitor;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;

import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
public final class EquationVariablesCollector extends EquationBaseVisitor<Byte> {
    private final Set<String> variables = new HashSet<>();
    private final String expression;

    @Override
    public Byte visitVariable(final EquationParser.VariableContext ctx) {
        variables.add(ctx.getText());
        return null;
    }

    public Set<String> collect() {
        variables.clear();
        visit(buildParser(expression).expression());
        return variables;
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

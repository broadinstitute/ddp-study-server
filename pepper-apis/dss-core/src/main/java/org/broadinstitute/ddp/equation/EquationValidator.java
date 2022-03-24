package org.broadinstitute.ddp.equation;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.broadinstitute.ddp.pex.lang.EquationLexer;
import org.broadinstitute.ddp.pex.lang.EquationParser;
import org.eclipse.jetty.util.StringUtil;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

@Slf4j
public final class EquationValidator implements ConstraintValidator<ValidEquation, String> {
    @Override
    public void initialize(final ValidEquation annotation) {
    }

    @Override
    public boolean isValid(final String expression, final ConstraintValidatorContext context) {
        return StringUtil.isNotBlank(expression) && isParseable(expression);
    }

    private boolean isParseable(final String expression) {
        try {
            buildParser(expression).expression();
            return true;
        } catch (final ParseCancellationException ignored) {
            log.error("Expression {} can't be parsed", expression);
        }

        return false;
    }

    private EquationParser buildParser(final String expression) {
        final EquationParser parser = new EquationParser(new CommonTokenStream(buildLexer(expression)));
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }

    private EquationLexer buildLexer(final String expression) {
        return new EquationLexer(CharStreams.fromString(expression));
    }
}

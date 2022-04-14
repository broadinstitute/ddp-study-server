package org.broadinstitute.ddp.pex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class FailFastLexerTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testLexing_works() {
        String text = "user.studies[\"A1\"].forms[\"B1\"].questions[\"C1\"].answers.hasTrue()";
        CharStream chars = CharStreams.fromString(text);
        FailFastLexer lexer = new FailFastLexer(chars);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();

        List<Token> list = tokens.getTokens();
        assertNotNull(list);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testLexing_fail() {
        thrown.expect(PexLexicalException.class);

        String text = "unsupported chars #@!";
        CharStream chars = CharStreams.fromString(text);
        FailFastLexer lexer = new FailFastLexer(chars);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        tokens.fill();
    }
}

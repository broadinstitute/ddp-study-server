package org.broadinstitute.ddp.pex;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.LexerNoViableAltException;
import org.antlr.v4.runtime.RecognitionException;
import org.broadinstitute.ddp.pex.lang.PexLexer;

/**
 * A fail fast lexer for the pex expression language that
 * throws lexical exception instead of trying to recover.
 */
public class FailFastLexer extends PexLexer {

    public FailFastLexer(CharStream input) {
        super(input);
    }

    @Override
    public void recover(LexerNoViableAltException e) {
        throw new PexLexicalException(e);
    }

    @Override
    public void recover(RecognitionException re) {
        throw new PexLexicalException(re);
    }
}

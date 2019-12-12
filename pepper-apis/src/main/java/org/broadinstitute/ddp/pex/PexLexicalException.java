package org.broadinstitute.ddp.pex;

/**
 * Indicates that a lexical error occurred while scanning
 * through the characters in a pex expression.
 */
public class PexLexicalException extends PexException {

    /**
     * Instantiate PexLexicalException object.
     *
     * @param cause the underlying lexical error
     */
    public PexLexicalException(Throwable cause) {
        super(cause);
    }
}

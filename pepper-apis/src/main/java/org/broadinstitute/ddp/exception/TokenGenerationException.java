package org.broadinstitute.ddp.exception;

/**
 * Exception thrown when the app encounters an problem generating a jwt token for a user.
 */
public class TokenGenerationException extends RuntimeException
{
    public TokenGenerationException(String message) {
        super(message);
    }

    public TokenGenerationException(String message, Throwable cause) {
        super(message,cause);
    }
}

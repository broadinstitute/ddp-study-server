package org.broadinstitute.lddp.exception;

/**
 * Exception thrown when the app encounters an invalid jwt token
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}

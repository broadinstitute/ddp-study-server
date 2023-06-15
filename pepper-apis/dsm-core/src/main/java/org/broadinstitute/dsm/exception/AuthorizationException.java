package org.broadinstitute.dsm.exception;

public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Exception e) {
        super(message, e);
    }
}

package org.broadinstitute.dsm.exception;

public class DSMAuthenticationException extends RuntimeException {

    public DSMAuthenticationException(String message) {
        super(message);
    }
    public DSMAuthenticationException(String message, Exception e) {
        super(message, e);
    }
}

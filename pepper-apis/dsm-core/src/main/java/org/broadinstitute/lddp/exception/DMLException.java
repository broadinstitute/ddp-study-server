package org.broadinstitute.lddp.exception;

public class DMLException extends RuntimeException {
    public DMLException(String message) {
        super(message);
    }

    public DMLException(String message, Throwable cause) {
        super(message, cause);
    }
}

package org.broadinstitute.dsm.exception;

public class DsmInternalError extends RuntimeException {
    public DsmInternalError(String message) {
        super(message);
    }

    public DsmInternalError(String message, Throwable e) {
        super(message, e);
    }
}

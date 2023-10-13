package org.broadinstitute.dsm.exception;

public class DSMBadRequestException extends RuntimeException {
    public DSMBadRequestException(String message) {
        super(message);
    }

    public DSMBadRequestException(String message, Exception cause) {
        super(message, cause);
    }
}

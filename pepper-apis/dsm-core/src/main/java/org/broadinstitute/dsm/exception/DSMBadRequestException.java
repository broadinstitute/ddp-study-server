package org.broadinstitute.dsm.exception;

public class DSMBadRequestException extends RuntimeException {
    public DSMBadRequestException(String message) {
        super(message);
    }
}

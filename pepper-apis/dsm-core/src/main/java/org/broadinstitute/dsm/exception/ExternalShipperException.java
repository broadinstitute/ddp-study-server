package org.broadinstitute.dsm.exception;

public class ExternalShipperException extends RuntimeException {

    public ExternalShipperException(String message) {
        super(message);
    }

    public ExternalShipperException(String message, Throwable cause) {
        super(message,cause);
    }
}

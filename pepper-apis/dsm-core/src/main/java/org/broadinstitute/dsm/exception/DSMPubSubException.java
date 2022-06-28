package org.broadinstitute.dsm.exception;

public class DSMPubSubException extends RuntimeException {
    public DSMPubSubException(String message, Throwable cause) {
        super(message, cause);
    }
}

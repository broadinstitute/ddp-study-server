package org.broadinstitute.dsm.exception;

/**
 * Exception thrown when there is an error placing
 * COVID-19 orders in CareEvolve
 */
public class CareEvolveException extends RuntimeException {

    public CareEvolveException(String message, Throwable cause) {
        super(message, cause);
    }

    public CareEvolveException(String message) {
        super(message);
    }
}

package org.broadinstitute.lddp.exception;

/**
 *
 */
public class DatStatRestApiException extends RuntimeException
{

    public DatStatRestApiException(String message) {
        super(message);
    }

    public DatStatRestApiException(String message, Throwable cause) {
        super(message,cause);
    }
}

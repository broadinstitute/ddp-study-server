package org.broadinstitute.lddp.exception;

public class DatStatKitRequestIdException extends RuntimeException
{
    public DatStatKitRequestIdException(String message) {
        super(message);
    }

    public DatStatKitRequestIdException(String message, Throwable cause) {
        super(message,cause);
    }
}
package org.broadinstitute.lddp.exception;

public class DatStatAddressValidationException extends RuntimeException
{
    public DatStatAddressValidationException(String message) {
        super(message);
    }

    public DatStatAddressValidationException(String message, Throwable cause) {
        super(message,cause);
    }
}

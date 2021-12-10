package org.broadinstitute.lddp.exception;

/**
 *
 */
public class DatStatNotificationException extends RuntimeException
{

    public DatStatNotificationException(String message) {
        super(message);
    }

    public DatStatNotificationException(String message, Throwable cause) {
        super(message,cause);
    }
}

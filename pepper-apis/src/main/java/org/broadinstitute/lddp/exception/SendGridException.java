package org.broadinstitute.lddp.exception;

/**
 *
 */
public class SendGridException extends RuntimeException
{
    public SendGridException(String message) {
        super(message);
    }

    public SendGridException(String message, Throwable cause) {
        super(message,cause);
    }
}

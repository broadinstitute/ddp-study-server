package org.broadinstitute.ddp.exception;

/**
 * Exception thrown when there is an unrecoverable issue with
 * configuration file values.
 */
public class InvalidConfigurationException extends DDPException {

    public InvalidConfigurationException(String message) {
        super(message);
    }
}

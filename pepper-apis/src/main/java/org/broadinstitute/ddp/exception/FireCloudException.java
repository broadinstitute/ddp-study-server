package org.broadinstitute.ddp.exception;

/**
 * Base of the exceptions that come out of the DAOs.
 */
public class FireCloudException extends DDPException {

    public FireCloudException() {}

    public FireCloudException(String message) {
        super(message);
    }

    public FireCloudException(String message, Throwable cause) {
        super(message, cause);
    }

    public FireCloudException(Throwable cause) {
        super(cause);
    }
}

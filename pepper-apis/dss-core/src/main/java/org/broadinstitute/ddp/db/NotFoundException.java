package org.broadinstitute.ddp.db;

/**
 * Indicates that we couldn't find a thing using a DAO
 */
public class NotFoundException extends DaoException {
    public NotFoundException() {
    }

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public NotFoundException(Throwable cause) {
        super(cause);
    }
}

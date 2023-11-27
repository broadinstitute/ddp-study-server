package org.broadinstitute.ddp.db;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Base of the exceptions that come out of the DAOs.
 */
public class DaoException extends DDPException {

    public DaoException() {}

    public DaoException(String message) {
        super(message);
    }

    public DaoException(String message, Throwable cause) {
        super(message, cause);
    }

    public DaoException(Throwable cause) {
        super(cause);
    }
}

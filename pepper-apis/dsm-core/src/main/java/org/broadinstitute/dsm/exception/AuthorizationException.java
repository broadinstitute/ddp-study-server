package org.broadinstitute.dsm.exception;

import org.broadinstitute.dsm.statics.UserErrorMessages;

public class AuthorizationException extends RuntimeException {
    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException() {
        super(UserErrorMessages.NO_RIGHTS);
    }

    public AuthorizationException(String message, Exception e) {
        super(message, e);
    }
}

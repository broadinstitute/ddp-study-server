package org.broadinstitute.ddp.exception;

public class UserExistsException extends Exception {
    public UserExistsException(String email) {
        super(email);
    }
}

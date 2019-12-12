package org.broadinstitute.ddp.housekeeping.handler;

public class MissingUserException extends RuntimeException {
    public MissingUserException(String s) {
        super(s);
    }
}

package org.broadinstitute.dsm.exception;

public class ESMissingParticipantDataException extends RuntimeException {
    public ESMissingParticipantDataException(String message) {
        super(message);
    }

    public ESMissingParticipantDataException(String message, Throwable e) {
        super(message, e);
    }

    public ESMissingParticipantDataException(Throwable e) {
        super(e);
    }
}

package org.broadinstitute.dsm.exception;

public class ESMissingParticipantData extends RuntimeException {
    public ESMissingParticipantData(String message) {
        super(message);
    }

    public ESMissingParticipantData(String message, Throwable e) {
        super(message, e);
    }

    public ESMissingParticipantData(Throwable e) {
        super(e);
    }
}

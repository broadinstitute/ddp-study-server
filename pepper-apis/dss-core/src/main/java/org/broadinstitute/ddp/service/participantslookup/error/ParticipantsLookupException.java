package org.broadinstitute.ddp.service.participantslookup.error;

import static java.lang.String.format;

import org.broadinstitute.ddp.exception.DDPException;

/**
 * Participants lookup exception
 */
public class ParticipantsLookupException extends DDPException {

    private final ParticipantsLookupErrorType errorType;
    private final String errorCode;

    public ParticipantsLookupException(ParticipantsLookupErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.errorCode = null;
    }

    public ParticipantsLookupException(ParticipantsLookupErrorType errorType, String errorCode, String message) {
        super(message);
        this.errorType = errorType;
        this.errorCode = errorCode;
    }

    public ParticipantsLookupErrorType getErrorType() {
        return errorType;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public  String getExtendedMessage() {
        return errorCode != null ? format(super.getMessage() + ". ErrorType:%s, errorCode:%s", errorType, errorCode) :
                format(super.getMessage() + ". ErrorType:%s", errorType);
    }
}

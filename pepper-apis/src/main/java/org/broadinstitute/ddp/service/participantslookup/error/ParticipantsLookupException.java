package org.broadinstitute.ddp.service.participantslookup.error;

import static java.lang.String.format;

import org.broadinstitute.ddp.exception.DDPException;
import org.elasticsearch.rest.RestStatus;

/**
 * Participants lookup exception
 */
public class ParticipantsLookupException extends DDPException {

    private final ParticipantsLookupErrorType errorType;
    private final RestStatus restStatus;

    public ParticipantsLookupException(ParticipantsLookupErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.restStatus = null;
    }

    public ParticipantsLookupException(ParticipantsLookupErrorType errorType, RestStatus restStatus, String message) {
        super(message);
        this.errorType = errorType;
        this.restStatus = restStatus;
    }

    public ParticipantsLookupErrorType getErrorType() {
        return errorType;
    }

    public RestStatus getRestStatus() {
        return restStatus;
    }

    public  String getExtendedMessage() {
        return restStatus != null ? format(super.getMessage() + ". ErrorType:%s, restStatus:%s", errorType, restStatus) :
                format(super.getMessage() + ". ErrorType:%s", errorType);
    }
}

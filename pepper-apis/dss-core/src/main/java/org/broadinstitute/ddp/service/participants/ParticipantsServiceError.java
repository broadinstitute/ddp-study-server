package org.broadinstitute.ddp.service.participants;

import lombok.NonNull;
import lombok.Value;

@Value
public class ParticipantsServiceError extends Exception {
    public enum Code {
        USER_EXISTS,
        STUDY_REGISTRATION_FAILED,

        /**
         * An unhandled error occurred in one of the service's dependencies. Details of the
         * error are included, see {@link java.lang.Exception#getCause()}
         */
        INTERNAL_ERROR
    }

    @NonNull
    private Code code;

    public ParticipantsServiceError(Code code) {
        super(code.toString());
        this.code = code;
    }

    public ParticipantsServiceError(Code code, String message) {
        super(message);
        this.code = code;
    }

    public ParticipantsServiceError(Code code, Throwable cause) {
        super(code.toString(), cause);
        this.code = code;
    }

    public ParticipantsServiceError(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

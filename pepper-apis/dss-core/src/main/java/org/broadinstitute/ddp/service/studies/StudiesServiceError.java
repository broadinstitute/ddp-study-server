package org.broadinstitute.ddp.service.studies;

import lombok.Value;

@Value
public class StudiesServiceError extends Exception {
    public enum Code {
        STUDY_NOT_FOUND,
        PARTICIPANT_ALREADY_REGISTERED,
        DATABASE_ERROR,
        NOT_IMPLEMENTED
    }

    private Code code;

    public StudiesServiceError(Code code) {
        // Works for now to expose the code in a usable way to
        // the superclasses, but not too thrilled about it.
        //
        // Open to suggestions.
        super(code.toString());
        this.code = code;
    }

    public StudiesServiceError(Code code, String message) {
        super(message);
        this.code = code;
    }

    public StudiesServiceError(Code code, Throwable cause) {
        super(code.toString(), cause);
        this.code = code;
    }

    public StudiesServiceError(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

package org.broadinstitute.ddp.studybuilder;

public class StudyBuilderException extends RuntimeException {

    public StudyBuilderException() {
    }

    public StudyBuilderException(String message) {
        super(message);
    }

    public StudyBuilderException(String message, Throwable cause) {
        super(message, cause);
    }

    public StudyBuilderException(Throwable cause) {
        super(cause);
    }
}

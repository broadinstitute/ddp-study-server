package org.broadinstitute.ddp.db;

public class QuestionStableIdExistsException extends DaoException {

    private long studyId;
    private String stableId;

    public QuestionStableIdExistsException(long studyId, String stableId, Throwable cause) {
        super(cause);
        this.studyId = studyId;
        this.stableId = stableId;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStableId() {
        return stableId;
    }
}

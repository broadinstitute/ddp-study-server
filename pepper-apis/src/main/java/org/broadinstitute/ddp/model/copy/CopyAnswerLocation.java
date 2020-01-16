package org.broadinstitute.ddp.model.copy;

public class CopyAnswerLocation extends CopyLocation {

    private long questionStableCodeId;
    private String questionStableId;

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
    }

    public CopyAnswerLocation(String questionStableId) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
    }

    public long getQuestionStableCodeId() {
        return questionStableCodeId;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }
}

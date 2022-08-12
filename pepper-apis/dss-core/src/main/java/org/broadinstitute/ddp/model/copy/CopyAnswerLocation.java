package org.broadinstitute.ddp.model.copy;

import org.broadinstitute.ddp.pex.UserType;

public class CopyAnswerLocation extends CopyLocation {

    private long questionStableCodeId;
    private String questionStableId;

    private String user;

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId, String user) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.user = user;
    }

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.user = UserType.USER;
    }

    public CopyAnswerLocation(String questionStableId, String user) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
        this.user = user;
    }

    public CopyAnswerLocation(String questionStableId) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
        this.user = UserType.USER;
    }

    public long getQuestionStableCodeId() {
        return questionStableCodeId;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public String getUser() {
        return user;
    }
}

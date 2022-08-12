package org.broadinstitute.ddp.model.copy;


public class CopyAnswerLocation extends CopyLocation {

    private long questionStableCodeId;
    private String questionStableId;

    private UserType user;

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId, String user) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.user = UserType.valueOf(user.toUpperCase());
    }

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.user = UserType.USER;
    }

    public CopyAnswerLocation(String questionStableId, UserType user) {
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

    public UserType getUser() {
        return user;
    }
}

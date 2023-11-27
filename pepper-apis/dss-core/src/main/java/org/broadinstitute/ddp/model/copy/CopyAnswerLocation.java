package org.broadinstitute.ddp.model.copy;


public class CopyAnswerLocation extends CopyLocation {

    private long questionStableCodeId;
    private String questionStableId;

    private UserType userType;

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId, String userType) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.userType = UserType.valueOf(userType.toUpperCase());
    }

    public CopyAnswerLocation(long id, long questionStableCodeId, String questionStableId) {
        super(id, CopyLocationType.ANSWER);
        this.questionStableCodeId = questionStableCodeId;
        this.questionStableId = questionStableId;
        this.userType = UserType.USER;
    }

    public CopyAnswerLocation(String questionStableId, UserType user) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
        this.userType = user;
    }

    public CopyAnswerLocation(String questionStableId) {
        super(CopyLocationType.ANSWER);
        this.questionStableId = questionStableId;
        this.userType = UserType.USER;
    }

    public long getQuestionStableCodeId() {
        return questionStableCodeId;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public UserType getUserType() {
        return userType;
    }
}

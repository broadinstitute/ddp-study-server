package org.broadinstitute.ddp.model.pdf;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.types.QuestionType;

public class CompositeAnswerSubstitution extends AnswerSubstitution {

    private long activityId;
    private QuestionType questionType = QuestionType.COMPOSITE;
    private String questionStableId;
    private List<AnswerSubstitution> childAnswerSubstitutions;

    public CompositeAnswerSubstitution(String placeholder, long activityId, String questionStableId) {
        super(placeholder, activityId, QuestionType.COMPOSITE, questionStableId);

        this.activityId = activityId;
        this.questionStableId = questionStableId;
    }

    public CompositeAnswerSubstitution(String placeholder, long activityId, String questionStableId,
                                       List<AnswerSubstitution> childAnswerSubstitutions) {
        super(placeholder, activityId, QuestionType.COMPOSITE, questionStableId);
        this.activityId = activityId;
        this.questionStableId = questionStableId;
        this.childAnswerSubstitutions = childAnswerSubstitutions;
    }

    public void addChildAnswerSubstitutions(AnswerSubstitution childSubstitution) {
        if (this.childAnswerSubstitutions == null) {
            childAnswerSubstitutions = new ArrayList<>();
        }
        childAnswerSubstitutions.add(childSubstitution);
    }

    public long getActivityId() {
        return activityId;
    }

    public QuestionType getQuestionType() {
        return questionType;
    }

    public String getQuestionStableId() {
        return questionStableId;
    }

    public List<AnswerSubstitution> getChildAnswerSubstitutions() {
        return childAnswerSubstitutions;
    }

}

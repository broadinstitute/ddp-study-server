package org.broadinstitute.ddp.model.pdf;

import static org.broadinstitute.ddp.model.pdf.SubstitutionType.ANSWER;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class AnswerSubstitution extends PdfSubstitution {

    private long activityId;
    private QuestionType questionType;
    private String questionStableId;

    @JdbiConstructor
    public AnswerSubstitution(@ColumnName("pdf_substitution_id") long id,
                              @ColumnName("pdf_template_id") long templateId,
                              @ColumnName("placeholder") String placeholder,
                              @ColumnName("activity_id") long activityId,
                              @ColumnName("question_type") QuestionType questionType,
                              @ColumnName("question_stable_id") String questionStableId) {
        super(id, templateId, ANSWER, placeholder);
        this.activityId = activityId;
        this.questionType = questionType;
        this.questionStableId = questionStableId;
    }

    public AnswerSubstitution(String placeholder, long activityId, QuestionType questionType, String questionStableId) {
        super(ANSWER, placeholder);
        this.activityId = activityId;
        this.questionType = questionType;
        this.questionStableId = questionStableId;
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
}

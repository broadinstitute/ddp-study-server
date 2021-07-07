package org.broadinstitute.ddp.model.pdf;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class BooleanAnswerSubstitution extends AnswerSubstitution {

    private boolean checkIfFalse;
    private String boundOption;

    @JdbiConstructor
    public BooleanAnswerSubstitution(@ColumnName("pdf_substitution_id") long id,
                                     @ColumnName("pdf_template_id") long templateId,
                                     @ColumnName("placeholder") String placeholder,
                                     @ColumnName("activity_id") long activityId,
                                     @ColumnName("question_stable_id") String questionStableId,
                                     @ColumnName("check_if_false") boolean checkIfFalse,
                                     @ColumnName("parent_question_stable_id") String parentQuestionStableId,
                                     @ColumnName("bound_option_stable_id") String boundOption) {
        super(id, templateId, placeholder, activityId, QuestionType.BOOLEAN, questionStableId, parentQuestionStableId);
        this.checkIfFalse = checkIfFalse;
        this.boundOption = boundOption;
    }

    public BooleanAnswerSubstitution(String placeholder, long activityId, String questionStableId,
                                     boolean checkIfFalse, String parentQuestionStableId, String boundOption) {
        super(placeholder, activityId, QuestionType.BOOLEAN, questionStableId, parentQuestionStableId);
        this.checkIfFalse = checkIfFalse;
        this.boundOption = boundOption;
    }

    public boolean checkIfFalse() {
        return checkIfFalse;
    }

    public String getBoundOption() {
        return boundOption;
    }
}

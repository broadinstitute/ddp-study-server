package org.broadinstitute.ddp.model.pdf;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class BooleanAnswerSubstitution extends AnswerSubstitution {

    private boolean checkIfFalse;

    @JdbiConstructor
    public BooleanAnswerSubstitution(@ColumnName("pdf_substitution_id") long id,
                                     @ColumnName("pdf_template_id") long templateId,
                                     @ColumnName("placeholder") String placeholder,
                                     @ColumnName("activity_id") long activityId,
                                     @ColumnName("question_stable_id") String questionStableId,
                                     @ColumnName("check_if_false") boolean checkIfFalse) {
        super(id, templateId, placeholder, activityId, QuestionType.BOOLEAN, questionStableId);
        this.checkIfFalse = checkIfFalse;
    }

    public BooleanAnswerSubstitution(String placeholder, long activityId, String questionStableId, boolean checkIfFalse) {
        super(placeholder, activityId, QuestionType.BOOLEAN, questionStableId);
        this.checkIfFalse = checkIfFalse;
    }

    public boolean checkIfFalse() {
        return checkIfFalse;
    }
}

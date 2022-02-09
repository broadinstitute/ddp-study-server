package org.broadinstitute.ddp.model.pdf;

import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.util.HashMap;
import java.util.Map;

public class PicklistAnswerSubstitution extends AnswerSubstitution {
    Map<String, String> placeholderMapping;

    @JdbiConstructor
    public PicklistAnswerSubstitution(@ColumnName("pdf_substitution_id") long id,
                                     @ColumnName("pdf_template_id") long templateId,
                                     @ColumnName("placeholder") String placeholder,
                                     @ColumnName("activity_id") long activityId,
                                     @ColumnName("question_stable_id") String questionStableId,
                                     @ColumnName("parent_question_stable_id") String parentQuestionStableId) {
        super(id, templateId, placeholder, activityId, QuestionType.PICKLIST, questionStableId, parentQuestionStableId);
        placeholderMapping = new HashMap<>();
    }

    public PicklistAnswerSubstitution(String placeholder, Map<String, String> placeholderMapping, long activityId,
                                      QuestionType questionType, String questionStableId, String parentQuestionStableId) {
        super(placeholder, activityId, questionType, questionStableId, parentQuestionStableId);
        this.placeholderMapping = placeholderMapping;
    }

    public void addPlaceholderMapping(String fieldName, String optionStableId) {
        if (fieldName != null && optionStableId != null) {
            placeholderMapping.put(fieldName, optionStableId);
        }
    }

    public Map<String, String> getPlaceholderMapping() {
        return placeholderMapping;
    }
}

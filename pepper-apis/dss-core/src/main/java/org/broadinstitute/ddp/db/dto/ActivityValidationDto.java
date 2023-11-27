package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.broadinstitute.ddp.model.activity.definition.template.Template;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@RequiredArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class ActivityValidationDto {
    @ColumnName("study_activity_id")
    private final long studyActivityId;

    @ColumnName("activity_validation_id")
    private final Long activityValidationId;

    @ColumnName("precondition_text")
    private final String preconditionText;

    @ColumnName("expression_text")
    private final String expressionText;

    @ColumnName("error_message_template_id")
    private final Long errorMessageTemplateId;

    private String errorMessage;
    private Template errorMessageTemplate;

    @Setter(AccessLevel.NONE)
    private List<String> affectedQuestionStableIds = new ArrayList<>();

    public ActivityValidationDto(
             long studyActivityId,
             Long activityValidationId,
             String preconditionText,
             String expressionText,
             Template errorMessageTemplate
    ) {
        this(studyActivityId, activityValidationId, preconditionText, expressionText, (Long) null);
        this.errorMessageTemplate = errorMessageTemplate;
    }

    public void addAffectedField(String field) {
        this.affectedQuestionStableIds.add(field);
    }

    public void addAffectedFields(List<String> fields) {
        this.affectedQuestionStableIds.addAll(fields);
    }

    public List<String> getAffectedQuestionStableIds() {
        return affectedQuestionStableIds;
    }
}

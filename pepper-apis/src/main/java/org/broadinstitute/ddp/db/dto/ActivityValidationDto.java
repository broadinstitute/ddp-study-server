package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.activity.definition.template.Template;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class ActivityValidationDto {
    private long studyActivityId;
    private Long activityValidationId;
    private String expressionText;
    private String preconditionText;
    private String errorMessage;
    private Long errorMessageTemplateId;
    private List<String> affectedQuestionStableIds = new ArrayList<>();
    private Template errorMessageTemplate;

    @JdbiConstructor
    public ActivityValidationDto(
             @ColumnName("study_activity_id") long studyActivityId,
             @ColumnName("activity_validation_id") Long activityValidationId,
             @ColumnName("precondition_text") String preconditionText,
             @ColumnName("expression_text") String expressionText,
             @ColumnName("error_message_template_id") Long errorMessageTemplateId
    ) {
        this.studyActivityId = studyActivityId;
        this.activityValidationId = activityValidationId;
        this.preconditionText = preconditionText;
        this.expressionText = expressionText;
        this.errorMessageTemplateId = errorMessageTemplateId;
    }

    public ActivityValidationDto(
             long studyActivityId,
             Long activityValidationId,
             String preconditionText,
             String expressionText,
             Template errorMessageTemplate
    ) {
        this.studyActivityId = studyActivityId;
        this.activityValidationId = activityValidationId;
        this.preconditionText = preconditionText;
        this.expressionText = expressionText;
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

    public long getStudyActivityId() {
        return studyActivityId;
    }

    public Long getActivityValidationId() {
        return activityValidationId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPreconditionText() {
        return preconditionText;
    }

    public String getExpressionText() {
        return expressionText;
    }

    public Long getErrorMessageTemplateId() {
        return errorMessageTemplateId;
    }

    public Template getErrorMessageTemplate() {
        return errorMessageTemplate;
    }

    public void setErrorMessageTemplate(Template errorMessageTemplate) {
        this.errorMessageTemplate = errorMessageTemplate;
    }
}

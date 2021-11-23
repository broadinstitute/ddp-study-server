package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class RuleDto implements Serializable {

    private RuleType ruleType;
    private long id;
    private long questionId;
    private boolean allowSave;
    private Long hintTemplateId;
    private long revisionId;

    @JdbiConstructor
    public RuleDto(
            @ColumnName("rule_type") RuleType ruleType,
            @ColumnName("question_id") long questionId,
            @ColumnName("validation_id") long id,
            @ColumnName("allow_save") boolean allowSave,
            @ColumnName("correction_hint_template_id") Long hintTemplateId,
            @ColumnName("revision_id") long revisionId) {
        this.ruleType = ruleType;
        this.id = id;
        this.questionId = questionId;
        this.allowSave = allowSave;
        this.hintTemplateId = hintTemplateId;
        this.revisionId = revisionId;
    }

    protected RuleDto(RuleDto other) {
        this.ruleType = other.ruleType;
        this.id = other.id;
        this.questionId = other.questionId;
        this.allowSave = other.allowSave;
        this.hintTemplateId = other.hintTemplateId;
        this.revisionId = other.revisionId;
    }

    public RuleType getRuleType() {
        return ruleType;
    }

    public long getId() {
        return id;
    }

    public long getQuestionId() {
        return questionId;
    }

    public Long getHintTemplateId() {
        return hintTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public boolean isAllowSave() {
        return allowSave;
    }
}

package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Getter
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class RuleDto implements Serializable {
    @ColumnName("rule_type")
    private RuleType ruleType;

    @ColumnName("question_id")
    private long questionId;

    @ColumnName("validation_id")
    private long id;

    @ColumnName("allow_save")
    private boolean allowSave;

    @ColumnName("correction_hint_template_id")
    private Long hintTemplateId;

    @ColumnName("revision_id")
    private long revisionId;

    protected RuleDto(RuleDto other) {
        this.ruleType = other.ruleType;
        this.id = other.id;
        this.questionId = other.questionId;
        this.allowSave = other.allowSave;
        this.hintTemplateId = other.hintTemplateId;
        this.revisionId = other.revisionId;
    }
}

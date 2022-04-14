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
    private final RuleType ruleType;

    @ColumnName("question_id")
    private final long questionId;

    @ColumnName("validation_id")
    private final long id;

    @ColumnName("allow_save")
    private final boolean allowSave;

    @ColumnName("correction_hint_template_id")
    private final Long hintTemplateId;

    @ColumnName("revision_id")
    private final long revisionId;

    protected RuleDto(final RuleDto other) {
        this(other.ruleType, other.questionId, other.id, other.allowSave, other.hintTemplateId, other.revisionId);
    }
}

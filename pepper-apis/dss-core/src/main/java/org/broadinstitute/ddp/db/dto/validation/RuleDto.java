package org.broadinstitute.ddp.db.dto.validation;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.ToString;
import lombok.Data;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class RuleDto implements Serializable {
    @ColumnName("rule_type")
    private final RuleType ruleType;

    @ColumnName("question_id")
    @ToString.Exclude
    private final long questionId;

    @ToString.Exclude
    @ColumnName("question_stable_id")
    private final String questionStableId;

    @ToString.Exclude
    @ColumnName("validation_id")
    private final long id;

    @ToString.Exclude
    @ColumnName("allow_save")
    private final boolean allowSave;

    @ToString.Exclude
    @ColumnName("correction_hint_template_id")
    private final Long hintTemplateId;

    @ToString.Exclude
    @ColumnName("revision_id")
    private final long revisionId;

    protected RuleDto(final RuleDto other) {
        this(other.ruleType, other.questionId, other.questionStableId,
                other.id, other.allowSave, other.hintTemplateId, other.revisionId);
    }
}

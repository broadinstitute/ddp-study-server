package org.broadinstitute.ddp.db.dto;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class QuestionDto implements Serializable {
    @ColumnName("question_type")
    private QuestionType type;

    @ColumnName("question_id")
    private long id;

    @ColumnName("stable_id")
    private String stableId;

    @ColumnName("prompt_template_id")
    private long promptTemplateId;

    @ColumnName("tooltip_template_id")
    private Long tooltipTemplateId;

    @ColumnName("info_header_template_id")
    private Long additionalInfoHeaderTemplateId;

    @ColumnName("info_footer_template_id")
    private Long additionalInfoFooterTemplateId;

    @ColumnName("activity_id")
    private long activityId;

    @ColumnName("is_restricted")
    private boolean restricted;

    @Getter(AccessLevel.NONE)
    @ColumnName("is_deprecated")
    private Boolean deprecated;

    @Getter(AccessLevel.NONE)
    @ColumnName("hide_number")
    private Boolean hideNumber;

    @Getter(AccessLevel.NONE)
    @ColumnName("is_write_once")
    private Boolean writeOnce;

    @ColumnName("revision_id")
    private long revisionId;

    @ColumnName("revision_start")
    private long revisionStart;

    @ColumnName("revision_end")
    private Long revisionEnd;

    protected QuestionDto(final QuestionDto other) {
        this(other.type, other.id, other.stableId, other.promptTemplateId, other.tooltipTemplateId,
                other.additionalInfoHeaderTemplateId, other.additionalInfoFooterTemplateId,
                other.activityId, other.restricted, other.deprecated, other.hideNumber,
                other.writeOnce, other.revisionId, other.revisionStart, other.revisionEnd);
    }

    public Set<Long> getTemplateIds() {
        var ids = new HashSet<Long>();
        ids.add(promptTemplateId);
        if (tooltipTemplateId != null) {
            ids.add(tooltipTemplateId);
        }
        if (additionalInfoHeaderTemplateId != null) {
            ids.add(additionalInfoHeaderTemplateId);
        }
        if (additionalInfoFooterTemplateId != null) {
            ids.add(additionalInfoFooterTemplateId);
        }
        return ids;
    }

    public final boolean isDeprecated() {
        return deprecated != null && deprecated;
    }

    public final boolean isWriteOnce() {
        return writeOnce != null && writeOnce;
    }

    public final boolean shouldHideNumber() {
        return hideNumber != null && hideNumber;
    }
}

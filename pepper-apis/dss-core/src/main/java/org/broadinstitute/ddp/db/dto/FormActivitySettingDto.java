package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Value
@AllArgsConstructor
public class FormActivitySettingDto {
    @ColumnName("form_activity_setting_id")
    long id;

    @ColumnName("list_style_hint_code")
    ListStyleHint listStyleHint;

    @ColumnName("introduction_section_id")
    Long introductionSectionId;

    @ColumnName("closing_section_id")
    Long closingSectionId;

    @ColumnName("readonly_hint_template_id")
    Long readonlyHintTemplateId;

    @ColumnName("last_updated_text_template_id")
    Long lastUpdatedTextTemplateId;

    @ColumnName("last_updated")
    LocalDateTime lastUpdated;

    @Accessors(fluent = true)
    @ColumnName("snapshot_substitutions_on_submit")
    boolean shouldSnapshotSubstitutionsOnSubmit;

    @Accessors(fluent = true)
    @ColumnName("snapshot_address_on_submit")
    boolean shouldSnapshotAddressOnSubmit;

    @ColumnName("revision_id")
    long revisionId;

    public Set<Long> getTemplateIds() {
        var ids = new HashSet<Long>();
        if (lastUpdatedTextTemplateId != null) {
            ids.add(lastUpdatedTextTemplateId);
        }
        if (readonlyHintTemplateId != null) {
            ids.add(readonlyHintTemplateId);
        }
        return ids;
    }
}

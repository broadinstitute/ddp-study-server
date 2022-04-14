package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class PicklistOptionDto implements Serializable {
    @ColumnName("picklist_option_id")
    private final long id;

    @ColumnName("picklist_option_stable_id")
    private final String stableId;

    @ColumnName("option_label_template_id")
    private final long optionLabelTemplateId;

    @ColumnName("tooltip_template_id")
    private final Long tooltipTemplateId;

    @ColumnName("detail_label_template_id")
    private final Long detailLabelTemplateId;

    @ColumnName("allow_details")
    private final boolean allowDetails;

    @ColumnName("is_exclusive")
    private final boolean exclusive;

    @Accessors(fluent = true)
    @ColumnName("is_default")
    private final boolean isDefault;

    @ColumnName("display_order")
    private int displayOrder;

    @ColumnName("revision_id")
    private final long revisionId;

    @ColumnName("revision_start_timestamp")
    private final Long revisionStartTimestamp;

    @ColumnName("revision_end_timestamp")
    private final Long revisionEndTimestamp;

    @ColumnName("nested_options_template_id")
    private final Long nestedOptionsTemplateId;

    private final List<PicklistOptionDto> nestedOptions = new ArrayList<>();

    public Set<Long> getTemplateIds() {
        var ids = new HashSet<Long>();
        ids.add(optionLabelTemplateId);
        if (detailLabelTemplateId != null) {
            ids.add(detailLabelTemplateId);
        }
        if (tooltipTemplateId != null) {
            ids.add(tooltipTemplateId);
        }
        if (nestedOptionsTemplateId != null) {
            ids.add(nestedOptionsTemplateId);
        }
        for (var nestedOption : nestedOptions) {
            ids.addAll(nestedOption.getTemplateIds());
        }
        return ids;
    }
}

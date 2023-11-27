package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class PicklistOptionDto implements Serializable {
    @ColumnName("picklist_option_id")
    private final long id;

    @ColumnName("picklist_option_stable_id")
    private String stableId;

    @Nullable
    @ColumnName("value")
    private String value;

    @ColumnName("option_label_template_id")
    private long optionLabelTemplateId;

    @ColumnName("tooltip_template_id")
    private Long tooltipTemplateId;

    @ColumnName("detail_label_template_id")
    private Long detailLabelTemplateId;

    @ColumnName("allow_details")
    private boolean allowDetails;

    @ColumnName("is_exclusive")
    private boolean exclusive;

    @Accessors(fluent = true)
    @ColumnName("is_default")
    private boolean isDefault;

    @ColumnName("display_order")
    private int displayOrder;

    @ColumnName("revision_id")
    private long revisionId;

    @ColumnName("revision_start_timestamp")
    private Long revisionStartTimestamp;

    @ColumnName("revision_end_timestamp")
    private Long revisionEndTimestamp;

    @ColumnName("nested_options_template_id")
    private Long nestedOptionsTemplateId;

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

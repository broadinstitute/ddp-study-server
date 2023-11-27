package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public final class MatrixOptionDto implements Serializable {
    @ColumnName("matrix_option_id")
    private final long id;

    @ColumnName("matrix_option_stable_id")
    private final String stableId;

    @ColumnName("option_label_template_id")
    private final long optionLabelTemplateId;

    @ColumnName("tooltip_template_id")
    private final Long tooltipTemplateId;

    @ColumnName("matrix_group_id")
    private final long groupId;

    @ColumnName("is_exclusive")
    private final boolean exclusive;

    @ColumnName("display_order")
    private int displayOrder;

    @ColumnName("revision_id")
    private final long revisionId;

    @ColumnName("revision_start_timestamp")
    private final Long revisionStartTimestamp;

    @ColumnName("revision_end_timestamp")
    private final Long revisionEndTimestamp;

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Set<Long> getTemplateIds() {
        var ids = new HashSet<Long>();
        ids.add(optionLabelTemplateId);
        if (tooltipTemplateId != null) {
            ids.add(tooltipTemplateId);
        }
        return ids;
    }
}

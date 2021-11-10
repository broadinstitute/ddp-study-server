package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class MatrixRowDto implements TimestampRevisioned, Serializable {

    private long id;
    private String stableId;
    private long questionLabelTemplateId;
    private Long tooltipTemplateId;
    private int displayOrder;
    private long revisionId;
    private Long revisionStartTimestamp;
    private Long revisionEndTimestamp;

    @JdbiConstructor
    public MatrixRowDto(@ColumnName("matrix_row_id") long id,
                        @ColumnName("matrix_row_stable_id") String stableId,
                        @ColumnName("row_label_template_id") long questionLabelTemplateId,
                        @ColumnName("tooltip_template_id") Long tooltipTemplateId,
                        @ColumnName("display_order") int displayOrder,
                        @ColumnName("revision_id") long revisionId,
                        @ColumnName("revision_start_timestamp") Long revisionStartTimestamp,
                        @ColumnName("revision_end_timestamp") Long revisionEndTimestamp) {
        this.id = id;
        this.stableId = stableId;
        this.questionLabelTemplateId = questionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
        this.revisionStartTimestamp = revisionStartTimestamp;
        this.revisionEndTimestamp = revisionEndTimestamp;
    }

    public long getId() {
        return id;
    }

    public String getStableId() {
        return stableId;
    }

    public long getQuestionLabelTemplateId() {
        return questionLabelTemplateId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public Long getRevisionStartTimestamp() {
        return revisionStartTimestamp;
    }

    public Long getRevisionEndTimestamp() {
        return revisionEndTimestamp;
    }


    public Set<Long> getTemplateIds() {
        var ids = new HashSet<Long>();
        ids.add(questionLabelTemplateId);

        if (tooltipTemplateId != null) {
            ids.add(tooltipTemplateId);
        }

        return ids;
    }
}

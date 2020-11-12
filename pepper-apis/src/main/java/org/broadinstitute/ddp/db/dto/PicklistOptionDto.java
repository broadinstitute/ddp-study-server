package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PicklistOptionDto implements TimestampRevisioned, Serializable {

    private long id;
    private String stableId;
    private long optionLabelTemplateId;
    private Long tooltipTemplateId;
    private Long detailLabelTemplateId;
    private boolean allowDetails;
    private boolean isExclusive;
    private int displayOrder;
    private long revisionId;
    private Long revisionStartTimestamp;
    private Long revisionEndTimestamp;
    private Long nestedOptionsTemplateId;
    private List<PicklistOptionDto> nestedOptions = new ArrayList<>();

    @JdbiConstructor
    public PicklistOptionDto(@ColumnName("picklist_option_id") long id,
                             @ColumnName("picklist_option_stable_id") String stableId,
                             @ColumnName("option_label_template_id") long optionLabelTemplateId,
                             @ColumnName("tooltip_template_id") Long tooltipTemplateId,
                             @ColumnName("detail_label_template_id") Long detailLabelTemplateId,
                             @ColumnName("allow_details") boolean allowDetails,
                             @ColumnName("is_exclusive") boolean isExclusive,
                             @ColumnName("display_order") int displayOrder,
                             @ColumnName("revision_id") long revisionId,
                             @ColumnName("revision_start_timestamp") Long revisionStartTimestamp,
                             @ColumnName("revision_end_timestamp") Long revisionEndTimestamp,
                             @ColumnName("nested_options_template_id") Long nestedOptionsTemplateId) {
        this.id = id;
        this.stableId = stableId;
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.tooltipTemplateId = tooltipTemplateId;
        this.detailLabelTemplateId = detailLabelTemplateId;
        this.allowDetails = allowDetails;
        this.isExclusive = isExclusive;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
        this.revisionStartTimestamp = revisionStartTimestamp;
        this.revisionEndTimestamp = revisionEndTimestamp;
        this.nestedOptionsTemplateId = nestedOptionsTemplateId;
    }

    public long getId() {
        return id;
    }

    public String getStableId() {
        return stableId;
    }

    public long getOptionLabelTemplateId() {
        return optionLabelTemplateId;
    }

    public Long getTooltipTemplateId() {
        return tooltipTemplateId;
    }

    public Long getDetailLabelTemplateId() {
        return detailLabelTemplateId;
    }

    public boolean getAllowDetails() {
        return allowDetails;
    }

    public boolean isExclusive() {
        return isExclusive;
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

    public Long getNestedOptionsTemplateId() {
        return nestedOptionsTemplateId;
    }

    public List<PicklistOptionDto> getNestedOptions() {
        return nestedOptions;
    }

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

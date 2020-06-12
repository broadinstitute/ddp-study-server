package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.instance.question.Tooltip;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PicklistOptionDto {

    private long id;
    private String stableId;
    private long optionLabelTemplateId;
    private Tooltip tooltip;
    private Long detailLabelTemplateId;
    private boolean allowDetails;
    private boolean isExclusive;
    private int displayOrder;
    private long revisionId;

    @JdbiConstructor
    public PicklistOptionDto(@ColumnName("picklist_option_id") long id,
                             @ColumnName("picklist_option_stable_id") String stableId,
                             @ColumnName("option_label_template_id") long optionLabelTemplateId,
                             @ColumnName("tt_tooltip_id") Long tooltipId,
                             @ColumnName("tt_text_template_id") Long tooltipTextTemplateId,
                             @ColumnName("detail_label_template_id") Long detailLabelTemplateId,
                             @ColumnName("allow_details") boolean allowDetails,
                             @ColumnName("is_exclusive") boolean isExclusive,
                             @ColumnName("display_order") int displayOrder,
                             @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.stableId = stableId;
        this.optionLabelTemplateId = optionLabelTemplateId;
        this.detailLabelTemplateId = detailLabelTemplateId;
        this.allowDetails = allowDetails;
        this.isExclusive = isExclusive;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
        if (tooltipId != null) {
            tooltip = new Tooltip(tooltipId, tooltipTextTemplateId);
        }
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

    public Tooltip getTooltip() {
        return tooltip;
    }

    // Used in sql.
    public Long getTooltipId() {
        return  tooltip != null ? tooltip.getTooltipId() : null;
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
}

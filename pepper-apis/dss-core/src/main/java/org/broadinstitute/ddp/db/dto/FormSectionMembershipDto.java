package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class FormSectionMembershipDto {

    private long id;
    private long activityId;
    private long sectionId;
    private int displayOrder;
    private long revisionId;

    @JdbiConstructor
    public FormSectionMembershipDto(
            @ColumnName("form_activity__form_section_id") long id,
            @ColumnName("form_activity_id") long activityId,
            @ColumnName("form_section_id") long sectionId,
            @ColumnName("display_order") int displayOrder,
            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.activityId = activityId;
        this.sectionId = sectionId;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getActivityId() {
        return activityId;
    }

    public long getSectionId() {
        return sectionId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public long getRevisionId() {
        return revisionId;
    }
}

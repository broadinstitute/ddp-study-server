package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PicklistGroupDto {

    private long id;
    private String stableId;
    private long nameTemplateId;
    private int displayOrder;
    private long revisionId;

    @JdbiConstructor
    public PicklistGroupDto(@ColumnName("picklist_group_id") long id,
                            @ColumnName("group_stable_id") String stableId,
                            @ColumnName("name_template_id") long nameTemplateId,
                            @ColumnName("display_order") int displayOrder,
                            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.stableId = stableId;
        this.nameTemplateId = nameTemplateId;
        this.displayOrder = displayOrder;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public String getStableId() {
        return stableId;
    }

    public long getNameTemplateId() {
        return nameTemplateId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public long getRevisionId() {
        return revisionId;
    }
}

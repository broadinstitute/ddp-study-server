package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class PicklistGroupDto implements TimestampRevisioned, Serializable {

    private long id;
    private String stableId;
    private long nameTemplateId;
    private int displayOrder;
    private long revisionId;
    private Long revisionStartTimestamp;
    private Long revisionEndTimestamp;

    @JdbiConstructor
    public PicklistGroupDto(@ColumnName("picklist_group_id") long id,
                            @ColumnName("group_stable_id") String stableId,
                            @ColumnName("name_template_id") long nameTemplateId,
                            @ColumnName("display_order") int displayOrder,
                            @ColumnName("revision_id") long revisionId,
                            @ColumnName("revision_start_timestamp") Long revisionStartTimestamp,
                            @ColumnName("revision_end_timestamp") Long revisionEndTimestamp) {
        this.id = id;
        this.stableId = stableId;
        this.nameTemplateId = nameTemplateId;
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

    public long getNameTemplateId() {
        return nameTemplateId;
    }

    public int getDisplayOrder() {
        return displayOrder;
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
}

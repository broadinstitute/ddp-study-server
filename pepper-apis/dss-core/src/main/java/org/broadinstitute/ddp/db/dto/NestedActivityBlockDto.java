package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NestedActivityBlockDto {

    private long id;
    private long blockId;
    private long nestedActivityId;
    private String nestedActivityCode;
    private NestedActivityRenderHint renderHint;
    private boolean allowMultiple;
    private Long addButtonTemplateId;
    private long revisionId;

    @JdbiConstructor
    public NestedActivityBlockDto(
            @ColumnName("block_nested_activity_id") long id,
            @ColumnName("block_id") long blockId,
            @ColumnName("nested_activity_id") long nestedActivityId,
            @ColumnName("nested_activity_code") String nestedActivityCode,
            @ColumnName("render_hint") NestedActivityRenderHint renderHint,
            @ColumnName("allow_multiple") boolean allowMultiple,
            @ColumnName("add_button_template_id") Long addButtonTemplateId,
            @ColumnName("revision_id") long revisionId) {
        this.id = id;
        this.blockId = blockId;
        this.nestedActivityId = nestedActivityId;
        this.nestedActivityCode = nestedActivityCode;
        this.renderHint = renderHint;
        this.allowMultiple = allowMultiple;
        this.addButtonTemplateId = addButtonTemplateId;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getBlockId() {
        return blockId;
    }

    public long getNestedActivityId() {
        return nestedActivityId;
    }

    public String getNestedActivityCode() {
        return nestedActivityCode;
    }

    public NestedActivityRenderHint getRenderHint() {
        return renderHint;
    }

    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    public Long getAddButtonTemplateId() {
        return addButtonTemplateId;
    }

    public long getRevisionId() {
        return revisionId;
    }
}

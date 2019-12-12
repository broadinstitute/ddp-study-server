package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ComponentDto {

    private long componentId;
    private ComponentType componentType;
    private boolean hideNumber;
    private long revisionId;

    @JdbiConstructor
    public ComponentDto(@ColumnName("component_id") long componentId,
                        @ColumnName("component_type") ComponentType componentType,
                        @ColumnName("hide_number") boolean hideNumber,
                        @ColumnName("revision_id") long revisionId) {
        this.componentId = componentId;
        this.componentType = componentType;
        this.hideNumber = hideNumber;
        this.revisionId = revisionId;
    }

    public ComponentType getComponentType() {
        return componentType;
    }

    public long getComponentId() {
        return componentId;
    }

    public boolean shouldHideNumber() {
        return hideNumber;
    }

    public long getRevisionId() {
        return revisionId;
    }
}

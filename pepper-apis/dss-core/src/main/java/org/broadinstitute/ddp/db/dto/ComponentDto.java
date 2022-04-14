package org.broadinstitute.ddp.db.dto;

import java.util.HashSet;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Data
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class ComponentDto {
    @ColumnName("component_id")
    private final long componentId;

    @ColumnName("component_type")
    private final ComponentType componentType;

    @Accessors(fluent = true)
    @ColumnName("hide_number")
    private final boolean shouldHideNumber;

    @ColumnName("revision_id")
    private final long revisionId;
    
    public ComponentDto(final ComponentDto other) {
        this(other.componentId, other.componentType, other.shouldHideNumber, other.revisionId);
    }
    
    public Set<Long> getTemplateIds() {
        return new HashSet<>();
    }
}

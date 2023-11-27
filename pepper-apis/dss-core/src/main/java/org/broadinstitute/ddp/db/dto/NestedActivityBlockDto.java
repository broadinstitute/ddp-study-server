package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.activity.types.NestedActivityRenderHint;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class NestedActivityBlockDto {
    @ColumnName("block_nested_activity_id")
    long id;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("nested_activity_id")
    long nestedActivityId;

    @ColumnName("nested_activity_code")
    String nestedActivityCode;

    @ColumnName("render_hint")
    NestedActivityRenderHint renderHint;

    @ColumnName("allow_multiple")
    boolean allowMultiple;

    @ColumnName("add_button_template_id")
    Long addButtonTemplateId;

    @ColumnName("revision_id")
    long revisionId;
}

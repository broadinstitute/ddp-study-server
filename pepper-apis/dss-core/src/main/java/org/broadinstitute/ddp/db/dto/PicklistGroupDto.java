package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.io.Serializable;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class PicklistGroupDto implements Serializable {
    @ColumnName("picklist_group_id")
    long id;

    @ColumnName("group_stable_id")
    String stableId;

    @ColumnName("name_template_id")
    long nameTemplateId;

    @ColumnName("display_order")
    int displayOrder;

    @ColumnName("revision_id")
    long revisionId;

    @ColumnName("revision_start_timestamp")
    Long revisionStartTimestamp;

    @ColumnName("revision_end_timestamp")
    Long revisionEndTimestamp;
}

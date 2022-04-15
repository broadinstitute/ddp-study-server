package org.broadinstitute.ddp.db.dto;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class MatrixGroupDto implements Serializable {
    @ColumnName("matrix_group_id")
    long id;

    @ColumnName("group_stable_id")
    String stableId;

    @ColumnName("name_template_id")
    Long nameTemplateId;

    @ColumnName("display_order")
    int displayOrder;

    @ColumnName("revision_id")
    long revisionId;

    @ColumnName("revision_start_timestamp")
    Long revisionStartTimestamp;

    @ColumnName("revision_end_timestamp")
    Long revisionEndTimestamp;
}

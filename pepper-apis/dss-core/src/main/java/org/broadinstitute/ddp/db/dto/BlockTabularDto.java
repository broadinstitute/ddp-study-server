package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockTabularDto {
    @ColumnName("block_tabular_id")
    long tabularBlockId;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("columns_count")
    int columnsCount;

    @ColumnName("revision_id")
    long revisionId;
}

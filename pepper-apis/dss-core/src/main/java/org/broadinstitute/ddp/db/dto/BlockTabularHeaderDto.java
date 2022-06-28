package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockTabularHeaderDto {
    @ColumnName("block_tabular_header_id")
    long tabularBlockHeaderId;

    @ColumnName("block_tabular_id")
    long tabularBlockId;

    @ColumnName("column_span")
    int columnSpan;

    @ColumnName("template_id")
    long templateId;
}

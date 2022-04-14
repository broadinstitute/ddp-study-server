package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockExpressionDto {
    @ColumnName("block__expression_id")
    long id;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("expression_id")
    long expressionId;

    @ColumnName("revision_id")
    long revisionId;
}

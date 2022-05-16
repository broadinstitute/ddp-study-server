package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class BlockTabularQuestionDto {
    @ColumnName("block_tabular_question_id")
    long id;

    @ColumnName("block_tabular_id")
    long tabularBlockId;

    @ColumnName("question_id")
    long questionId;

    @ColumnName("block_id")
    long blockId;

    @ColumnName("_column")
    int column;

    @ColumnName("_row")
    int row;
}

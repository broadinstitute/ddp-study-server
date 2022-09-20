package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.mapper.Nested;
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

    @ColumnName("column_span")
    int columnSpan;

    @ColumnName("question_block_id")
    long questionBlockId;

    @ColumnName("block_guid")
    String blockGuid;

    @Nested("sh_expr")
    Expression shownExpression;

    @Nested("en_expr")
    Expression enabledExpression;

    public String getShownExpr() {
        return shownExpression.getText();
    }

    public String getEnabledExpr() {
        return enabledExpression.getText();
    }

}

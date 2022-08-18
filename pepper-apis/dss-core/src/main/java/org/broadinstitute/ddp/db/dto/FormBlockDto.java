package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;

import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * A wrapper data object around a block and its optional conditional expression.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class FormBlockDto {

    @ColumnName("section_id")
    Long sectionId;

    @ColumnName("parent_block_id")
    Long parentBlockId;

    @Nested("block_meta")
    BlockDto blockDto;

    @Nested("sh_expr")
    Expression shownExpression;

    @Nested("en_expr")
    Expression enabledExpression;

    public BlockType getType() {
        return blockDto.getType();
    }

    public long getId() {
        return blockDto.getId();
    }

    public String getGuid() {
        return blockDto.getGuid();
    }

    public String getShownExpr() {
        if (shownExpression == null) {
            return null;
        }

        return shownExpression.getText();
    }

    public String getEnabledExpr() {
        if (enabledExpression == null) {
            return null;
        }
        
        return enabledExpression.getText();
    }

}

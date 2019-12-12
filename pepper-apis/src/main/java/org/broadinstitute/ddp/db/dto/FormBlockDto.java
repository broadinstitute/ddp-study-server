package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * A wrapper data object around a block and its optional conditional expression.
 */
public class FormBlockDto {

    private BlockDto blockDto;
    private String shownExpr;

    public FormBlockDto(BlockDto blockDto, String shownExpr) {
        this.blockDto = blockDto;
        this.shownExpr = shownExpr;
    }

    public BlockDto getBlockDto() {
        return blockDto;
    }

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
        return shownExpr;
    }

    public static class FormBlockDtoMapper implements RowMapper<FormBlockDto> {
        @Override
        public FormBlockDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new FormBlockDto(new BlockDto.BlockDtoMapper().map(rs, ctx), rs.getString(SqlConstants.ExpressionTable.TEXT));
        }
    }
}

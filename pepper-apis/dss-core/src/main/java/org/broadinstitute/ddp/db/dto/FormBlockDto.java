package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.constants.SqlConstants;

import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * A wrapper data object around a block and its optional conditional expression.
 */
@Value
@AllArgsConstructor
public class FormBlockDto {
    Long sectionId;
    Long parentBlockId;
    BlockDto blockDto;
    String shownExpr;
    String enabledExpr;

    public static class FormBlockDtoMapper implements RowMapper<FormBlockDto> {
        @Override
        public FormBlockDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new FormBlockDto(
                    (Long) rs.getObject("form_section_id"),
                    (Long) rs.getObject("parent_block_id"),
                    new BlockDto.BlockDtoMapper().map(rs, ctx),
                    rs.getString("shown_expression_text"),
                    rs.getString("enabled_expression_text"));
        }
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

    public static class FormBlockDtoMapper implements RowMapper<FormBlockDto> {
        @Override
        public FormBlockDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new FormBlockDto(
                    rs.getLong("form_section_id"),
                    rs.getLong("parent_block_id"),
                    new BlockDto(
                            BlockType.valueOf(rs.getString(SqlConstants.BlockTypeTable.CODE)),
                            rs.getLong(SqlConstants.BlockTable.ID),
                            rs.getString(SqlConstants.BlockTable.GUID)),
                    rs.getString("shown_expression_text"),
                    rs.getString("enabled_expression_text"));
        }
    }
}

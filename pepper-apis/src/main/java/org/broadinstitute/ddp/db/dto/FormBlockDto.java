package org.broadinstitute.ddp.db.dto;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

/**
 * A wrapper data object around a block and its optional conditional expression.
 */
public class FormBlockDto {

    private final Long sectionId;
    private final Long parentBlockId;
    private final BlockDto blockDto;
    private final String shownExpr;
    private final String enabledExpr;

    public FormBlockDto(Long sectionId, Long parentBlockId, BlockDto blockDto, String shownExpr, String enabledExpr) {
        this.sectionId = sectionId;
        this.parentBlockId = parentBlockId;
        this.blockDto = blockDto;
        this.shownExpr = shownExpr;
        this.enabledExpr = enabledExpr;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public Long getParentBlockId() {
        return parentBlockId;
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

    public String getEnabledExpr() {
        return enabledExpr;
    }

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
}

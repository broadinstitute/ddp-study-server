package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockExpressionTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class BlockExpressionDto {

    private long id;
    private long blockId;
    private long expressionId;
    private long revisionId;

    public BlockExpressionDto(long id, long blockId, long expressionId, long revisionId) {
        this.id = id;
        this.blockId = blockId;
        this.expressionId = expressionId;
        this.revisionId = revisionId;
    }

    public long getId() {
        return id;
    }

    public long getBlockId() {
        return blockId;
    }

    public long getExpressionId() {
        return expressionId;
    }

    public long getRevisionId() {
        return revisionId;
    }

    public static class BlockExpressionDtoMapper implements RowMapper<BlockExpressionDto> {
        @Override
        public BlockExpressionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new BlockExpressionDto(
                    rs.getLong(BlockExpressionTable.ID),
                    rs.getLong(BlockExpressionTable.BLOCK_ID),
                    rs.getLong(BlockExpressionTable.EXPRESSION_ID),
                    rs.getLong(BlockExpressionTable.REVISION_ID));
        }
    }
}

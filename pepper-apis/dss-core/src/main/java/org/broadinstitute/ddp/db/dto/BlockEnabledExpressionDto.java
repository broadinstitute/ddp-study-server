package org.broadinstitute.ddp.db.dto;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockEnabledExpressionTable;

public class BlockEnabledExpressionDto {

    private final long id;
    private final long blockId;
    private final long expressionId;
    private final long revisionId;

    public BlockEnabledExpressionDto(long id, long blockId, long expressionId, long revisionId) {
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

    public static class BlockEnabledExpressionDtoMapper implements RowMapper<BlockEnabledExpressionDto> {
        @Override
        public BlockEnabledExpressionDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new BlockEnabledExpressionDto(
                    rs.getLong(BlockEnabledExpressionTable.ID),
                    rs.getLong(BlockEnabledExpressionTable.BLOCK_ID),
                    rs.getLong(BlockEnabledExpressionTable.EXPRESSION_ID),
                    rs.getLong(BlockEnabledExpressionTable.REVISION_ID));
        }
    }
}

package org.broadinstitute.ddp.db.dto;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockTable;
import static org.broadinstitute.ddp.constants.SqlConstants.BlockTypeTable;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;

public class BlockDto {

    private BlockType type;
    private long id;
    private String guid;

    public BlockDto(BlockType type, long id, String guid) {
        this.type = type;
        this.id = id;
        this.guid = guid;
    }

    public BlockType getType() {
        return type;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public static class BlockDtoMapper implements RowMapper<BlockDto> {
        @Override
        public BlockDto map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new BlockDto(
                    BlockType.valueOf(rs.getString(BlockTypeTable.CODE)),
                    rs.getLong(BlockTable.ID),
                    rs.getString(BlockTable.GUID));
        }
    }
}

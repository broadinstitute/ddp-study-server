package org.broadinstitute.ddp.db.dao;

import static org.broadinstitute.ddp.constants.SqlConstants.BlockTable;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dto.BlockDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBlock extends SqlObject {

    default String generateUniqueGuid() {
        return DBUtils.uniqueStandardGuid(getHandle(), BlockTable.TABLE_NAME, BlockTable.GUID);
    }

    @SqlUpdate("insert into block(block_type_id,block_guid) values(?,?)")
    @GetGeneratedKeys()
    long insert(long blockTypeId, String blockGuid);

    @SqlQuery("select block_type_code from block_type where block_type_id = ?")
    String getBlockType(long blockTypeId);

    @SqlQuery("select bt.block_type_code, b.* from block as b, block_type as bt where"
            + " b.block_id = ? and b.block_type_id = bt.block_type_id")
    @RegisterRowMapper(BlockDto.BlockDtoMapper.class)
    BlockDto findById(long blockId);
}

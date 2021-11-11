package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiBlockType extends SqlObject {

    @SqlQuery("select block_type_id from block_type where block_type_code = :blockType")
    long getTypeId(@Bind("blockType")BlockType blockType);
}

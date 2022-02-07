package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiBlockComponent extends SqlObject {

    @SqlUpdate("insert into block_component(block_id,component_id) values (:blockId,:componentId)")
    @GetGeneratedKeys
    long insert(@Bind("blockId") long blockId, @Bind("componentId") long componentId);
}

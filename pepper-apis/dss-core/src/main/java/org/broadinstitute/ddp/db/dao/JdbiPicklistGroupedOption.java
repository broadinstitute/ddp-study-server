package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;

public interface JdbiPicklistGroupedOption extends SqlObject {

    @SqlBatch("insert into picklist_grouped_option (picklist_group_id, picklist_option_id) values (:groupId, :optionId)")
    @GetGeneratedKeys
    long[] bulkInsert(@Bind("groupId") long groupId, @Bind("optionId") List<Long> optionIds);
}

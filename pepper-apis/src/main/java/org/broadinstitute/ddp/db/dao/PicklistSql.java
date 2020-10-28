package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlBatch;

public interface PicklistSql extends SqlObject {

    @SqlBatch("insert into picklist_nested_option (parent_option_id, nested_option_id) values (:optionId, :nestedOptionId)")
    int[] bulkInsertNestedOptions(@Bind("optionId") long optionId, @Bind("nestedOptionId") List<Long> nestedOptionIds);

}

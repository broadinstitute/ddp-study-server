package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiValidationType extends SqlObject {

    @SqlQuery("select validation_type_id from validation_type where validation_type_code = :typeCode")
    long getTypeId(RuleType typeCode);
}

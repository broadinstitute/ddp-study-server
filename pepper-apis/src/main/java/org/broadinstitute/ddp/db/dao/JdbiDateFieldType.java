package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiDateFieldType extends SqlObject {

    @SqlQuery("select date_field_type_id from date_field_type where date_field_type_code = :dateFieldCode")
    long getTypeId(DateFieldType dateFieldCode);
}

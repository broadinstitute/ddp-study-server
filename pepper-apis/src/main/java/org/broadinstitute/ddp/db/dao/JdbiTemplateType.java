package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

public interface JdbiTemplateType extends SqlObject {

    @SqlQuery("select template_type_id from template_type where template_type_code = ?")
    long getTypeId(TemplateType type);
}

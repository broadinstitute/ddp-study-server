package org.broadinstitute.ddp.db.dao;

import java.util.List;

import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

@UseStringTemplateSqlLocator
public interface JdbiDateQuestionFieldOrder extends SqlObject {

    @SqlQuery("queryOrderedFieldsByQuestionId")
    List<DateFieldType> getOrderedFieldsByQuestionId(long questionId);

    @SqlUpdate("insert")
    @GetGeneratedKeys
    long insert(long questionId, long dateFieldTypeId, int displayOrder);
}

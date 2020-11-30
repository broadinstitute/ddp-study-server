package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

@UseStringTemplateSqlLocator
public interface JdbiDateQuestionFieldOrder extends SqlObject {

    @SqlUpdate("insert")
    @GetGeneratedKeys
    long insert(@Bind("questionId") long questionId, @Bind("dateFieldTypeId") long dateFieldTypeId, @Bind("displayOrder") int displayOrder);
}

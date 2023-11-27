package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


public interface JdbiDateQuestionFieldOrder extends SqlObject {

    @UseStringTemplateSqlLocator
    @SqlUpdate("insert")
    @GetGeneratedKeys
    long insert(@Bind("questionId") long questionId, @Bind("dateFieldTypeId") long dateFieldTypeId, @Bind("displayOrder") int displayOrder);

    @SqlUpdate("DELETE FROM date_question_field_order WHERE date_question_id = :questionId")
    int deleteForQuestionId(@Bind("questionId")long questionId);
}

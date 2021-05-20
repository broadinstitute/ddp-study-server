package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;


@UseStringTemplateSqlLocator
public interface JdbiFileQuestion extends SqlObject {

    @SqlUpdate("insert")
    int insert(@Bind("questionId") long questionId, @Bind("maxFileSize") long maxFileSize);

    @SqlUpdate("insertMimeType")
    @GetGeneratedKeys
    long insertMimeType(@Bind("mimeType") String mimeType);

    @SqlUpdate("insertFileQuestionMimeType")
    int insertFileQuestionMimeType(@Bind("fileQuestionId") long fileQuestionId, @Bind("mimeTypeId") long mimeTypeId);
}

package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;


public interface JdbiFileQuestion extends SqlObject {

    @SqlUpdate("insert into file_question (question_id, max_file_size) values (:questionId, :maxFileSize)")
    int insert(@Bind("questionId") long questionId, @Bind("maxFileSize") long maxFileSize);

    @SqlUpdate("insert into mime_type (mime_type_code) values (:mimeTypeCode)")
    @GetGeneratedKeys
    long insertMimeType(@Bind("mimeTypeCode") String mimeTypeCode);

    @SqlUpdate("insert into file_question__mime_type (file_question_id, mime_type_id) values (:fileQuestionId, :mimeTypeId)")
    int insertFileQuestionMimeType(@Bind("fileQuestionId") long fileQuestionId, @Bind("mimeTypeId") long mimeTypeId);
}

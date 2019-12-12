package org.broadinstitute.ddp.db.dao;

import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.QuestionStableIdExistsException;
import org.jdbi.v3.core.statement.UnableToExecuteStatementException;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiQuestionStableCode extends SqlObject {

    @SqlUpdate("insert into question_stable_code (umbrella_study_id, stable_id) values (:studyId, :stableId)")
    @GetGeneratedKeys
    long insert(@Bind("studyId")long studyId, @Bind("stableId")String stableId);

    @SqlQuery("select stable_id from question_stable_code where question_stable_code_id = :id")
    String getStableIdForId(@Bind("id") Long id);

    @SqlQuery("select question_stable_code_id from question_stable_code where stable_id = :stableId and umbrella_study_id=:studyId")
    Optional<Long> getIdForStableId(@Bind("stableId") String stableId, @Bind("studyId") long studyId);

    default long insertStableId(long studyId, String stableId) {
        try {
            return insert(studyId, stableId);
        } catch (UnableToExecuteStatementException e) {
            if (e.getCause() instanceof SQLIntegrityConstraintViolationException) {
                throw new QuestionStableIdExistsException(studyId, stableId, e.getCause());
            } else {
                throw new DaoException(e.getCause());
            }
        }
    }
}

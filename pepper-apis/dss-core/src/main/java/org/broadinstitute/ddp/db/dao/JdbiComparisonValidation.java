package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiComparisonValidation {
    @SqlUpdate("insert into comparison_validation (validation_id, reference_question_id, comparison_validation_type) "
             + " values (:validationId, :referenceQuestionId, :comparisonType)")
    int insert(long validationId, long referenceQuestionId, ComparisonType comparisonType);
}

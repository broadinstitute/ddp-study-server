package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiConsentCondition extends SqlObject {

    @SqlUpdate("insert into consent_condition(form_activity_id,consented_expression_id,revision_id)"
            + "values(:consentActivityId,:expressionId,:revisionId)")
    @GetGeneratedKeys
    long insert(long consentActivityId, long expressionId, long revisionId);

}

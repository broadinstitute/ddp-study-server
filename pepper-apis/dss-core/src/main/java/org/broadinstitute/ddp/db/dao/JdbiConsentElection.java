package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiConsentElection extends SqlObject {

    @SqlUpdate("insert into consent_election"
            + " (form_activity_id,election_stable_id,election_selected_expression_id,revision_id)"
            + " values(:formActivityId,:stableId,:expressionId,:revisionId)")
    @GetGeneratedKeys
    long insert(long formActivityId, String stableId, long expressionId, long revisionId);

}

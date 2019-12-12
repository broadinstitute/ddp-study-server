package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowStatusTrigger extends SqlObject {

    @SqlUpdate("insert into workflow_state_trigger(event_trigger_id,workflow_state_id,trigger_automatically) "
            + " values (:triggerId,:workflowStateId,:triggerAutomatically)")
    boolean insert(@Bind("triggerId") long triggerId,
                @Bind("workflowStateId") long workflowStateId,
                @Bind("triggerAutomatically") boolean triggerAutomatically);
}

package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowTransition extends SqlObject {

    @SqlUpdate("insert into workflow_transition (umbrella_study_id, from_state_id, next_state_id,"
            + " precondition_expression_id, execution_order, is_active)"
            + " values (:studyId, :fromStateId, :nextStateId, :preconditionId, :order, true)")
    @GetGeneratedKeys
    long insertActive(@Bind("studyId") long studyId,
                      @Bind("fromStateId") long fromStateId,
                      @Bind("nextStateId") long nextStateId,
                      @Bind("preconditionId") long preconditionId,
                      @Bind("order") int order);

    @SqlUpdate("update workflow_transition set is_active = :isActive where workflow_transition_id = :transitionId")
    int updateIsActiveById(@Bind("transitionId") long transitionId, @Bind("isActive") boolean isActive);

    @SqlUpdate("delete from workflow_transition where workflow_transition_id = :transitionId")
    int deleteById(@Bind("transitionId") long transitionId);
}

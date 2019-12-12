package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.workflow.StateType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowState extends SqlObject {

    @SqlUpdate("insert into workflow_state (workflow_state_type_id)"
            + " select workflow_state_type_id from workflow_state_type where workflow_state_type_code = :type")
    @GetGeneratedKeys
    long insert(@Bind("type") StateType type);

    @SqlQuery("select ws.workflow_state_id from workflow_state as ws"
            + " join workflow_state_type as wst on wst.workflow_state_type_id = ws.workflow_state_type_id"
            + " where wst.workflow_state_type_code = :type")
    Optional<Long> findIdByType(@Bind("type") StateType type);
}

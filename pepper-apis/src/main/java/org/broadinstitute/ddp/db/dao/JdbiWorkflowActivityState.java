package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowActivityState extends SqlObject {

    @SqlUpdate("insert into workflow_activity_state (workflow_state_id, study_activity_id) values (:stateId, :activityId)")
    int insert(@Bind("stateId") long stateId, @Bind("activityId") long activityId);

    @SqlQuery("select workflow_state_id from workflow_activity_state where study_activity_id = :activityId")
    Optional<Long> findIdByActivityId(@Bind("activityId") long activityId);
}

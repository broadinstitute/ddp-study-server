package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityInstanceCreationAction extends SqlObject {

    @SqlUpdate(
            "insert into activity_instance_creation_action (activity_instance_creation_action_id, study_activity_id)"
            + " values (:eventActionId, :studyActivityId)"
    )
    int insert(
            @Bind("eventActionId") long eventActionId,
            @Bind("studyActivityId") long studyActivityId
    );

    @SqlUpdate(
            "delete from activity_instance_creation_action"
            + " where activity_instance_creation_action_id = :activityInstanceCreationActionId"
    )
    int deleteById(@Bind("activityInstanceCreationActionId") long activityInstanceCreationActionId);

}

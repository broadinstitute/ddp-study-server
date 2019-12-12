package org.broadinstitute.ddp.db.dao;

import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityStatusTrigger extends SqlObject {
    @SqlUpdate("insert into activity_status_trigger (activity_status_trigger_id, study_activity_id, activity_instance_status_type_id)"
            + " values (:triggerId, :activityId, (select activity_instance_status_type_id from activity_instance_status_type"
            + " where activity_instance_status_type_code = :statusType))")
    int insert(@Bind("triggerId") long eventTriggerId,
               @Bind("activityId") long activityId,
               @Bind("statusType") InstanceStatusType statusType);

    @SqlUpdate("delete from activity_status_trigger where activity_status_trigger_id = :activityStatusTriggerId")
    int deleteById(@Bind("activityStatusTriggerId") long activityStatusTriggerId);
}

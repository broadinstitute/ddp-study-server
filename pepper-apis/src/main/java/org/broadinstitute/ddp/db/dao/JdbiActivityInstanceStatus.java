package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityInstanceStatus extends SqlObject {

    @SqlQuery(
            "select activity_instance_status_type_id from activity_instance_status_type"
            + " where activity_instance_status_type_code = :code"
    )
    long getIdByCode(@Bind("code") String code);

    @SqlUpdate("delete from activity_instance_status where"
            + " activity_instance_id = :activityInstanceId and updated_at = :updatedAt"
            + " and activity_instance_status_type_id = :typeId")
    void deleteStatusByActivityIdAndUpdatedAtAndStatusType(long activityInstanceId, long updatedAt, long typeId);

    @SqlUpdate("delete from activity_instance_status where"
            + " activity_instance_id = :id and updated_at = :updatedAt")
    void deleteMostRecentStatusByActivityId(@Bind long id, @Bind long updatedAt);

    @CreateSqlObject
    JdbiActivityInstance getActivityInstanceDao();

    @CreateSqlObject
    JdbiUser getUserDao();

    /**
     * In general, clients should avoid this low-level method and instead use the higher-level DAOs because they also
     * take care of handling housekeeping operations associated with status changes.
     *
     * @see ActivityInstanceStatusDao
     */
    @SqlUpdate("insert into activity_instance_status(activity_instance_id, activity_instance_status_type_id, "
            + "updated_at, operator_id) values(:instanceId,(SELECT activity_instance_status_type_id FROM activity_instance_status_type "
            + "WHERE activity_instance_status_type_code = :instanceStatusType),:epochMillis,:operatorId)")
    @GetGeneratedKeys
    long insert(@Bind("instanceId") long instanceId, @Bind("instanceStatusType") InstanceStatusType instanceStatusType,
                @Bind("epochMillis") long epochMillis, @Bind("operatorId") long operatorId);

    // Note: this should only be used in tests.
    @SqlUpdate("delete from activity_instance_status where activity_instance_status_id = ?")
    int deleteByStatusId(long statusId);

    @SqlQuery("select status.*, ty.activity_instance_status_type_code"
            + "  from activity_instance_status as status"
            + "  join activity_instance_status_type as ty on ty.activity_instance_status_type_id = status.activity_instance_status_type_id"
            + " where status.activity_instance_status_id = :id")
    @RegisterConstructorMapper(ActivityInstanceStatusDto.class)
    Optional<ActivityInstanceStatusDto> findByStatusId(@Bind("id") long statusId);

    @SqlUpdate("update activity_instance_status set updated_at = :timestamp where activity_instance_status_id = :id")
    int updateTimestampByStatusId(@Bind("id") long statusId, @Bind("timestamp") long timestamp);
}

package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiEventConfiguration extends SqlObject {

    @SqlUpdate("insert into event_configuration"
            + "(event_trigger_id,event_action_id,umbrella_study_id,created_at,is_active,max_occurrences_per_user,"
            + "post_delay_seconds,precondition_expression_id,cancel_expression_id, dispatch_to_housekeeping, execution_order) "
            + " values(:triggerId,:actionId,:studyId,:createdAt,true,:maxOccurrencesPerUser,:postDelay,:preconditionId,"
            + ":cancelId, :dispatchToHousekeeping, :executionOrder)")
    @GetGeneratedKeys
    long insert(
            @Bind("triggerId") long eventTriggerId,
            @Bind("actionId") long eventActionId,
            @Bind("studyId") long studyId,
            @Bind("createdAt") long createdAtMillis,
            @Bind("maxOccurrencesPerUser") Integer maxOccurencesPerUser,
            @Bind("postDelay") Integer postDelaySeconds,
            @Bind("preconditionId") Long preconditionExpressionId,
            @Bind("cancelId") Long cancelExpressionId,
            @Bind("dispatchToHousekeeping") boolean dispatchToHousekeeping,
            @Bind("executionOrder") int executionOrder
    );

    @SqlUpdate("delete from event_configuration where event_configuration_id = :eventConfigurationId")
    int deleteById(@Bind("eventConfigurationId") long eventConfigurationId);

    @SqlUpdate("delete from event_configuration")
    int deleteAll();

    @SqlUpdate(
            "update event_configuration set max_occurrences_per_user = :maxOccurrences"
            + " where event_configuration_id = :eventConfigurationId"
    )
    int updateMaxOccurrencesPerUserById(
            @Bind("eventConfigurationId") long eventConfigurationId,
            @Bind("maxOccurrences") Integer maxOccurrences
    );

    @SqlQuery("select umbrella_study_id from event_configuration where event_configuration_id = :eventConfigurationId")
    long getUmbrellaStudyIdByConfigurationId(@Bind("eventConfigurationId") long eventConfigurationId);

}


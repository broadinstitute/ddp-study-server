package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.dto.ActivityConditionDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiActivityCondition extends SqlObject {

    @SqlUpdate("insert into activity_condition (study_activity_id, creation_expression_id)"
            + " values (:activityId, :creationExprId)")
    int insert(@Bind("activityId") long activityId, @Bind("creationExprId") long creationExprId);

    @SqlUpdate("delete from activity_condition where study_activity_id = :studyActivityId")
    int deleteById(@Bind("studyActivityId") long studyActivityId);

    @SqlQuery(
            "select ac.study_activity_id, ac.creation_expression_id, e.expression_text as creation_expression"
            + " from activity_condition ac, expression e"
            + " where ac.creation_expression_id = e.expression_id and study_activity_id = :activityId"
    )
    @RegisterRowMapper(ActivityConditionDto.ActivityConditionDtoMapper.class)
    Optional<ActivityConditionDto> getById(@Bind("activityId") long activityId);
}

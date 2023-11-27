package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.workflow.StateType;
import org.broadinstitute.ddp.model.workflow.StudyRedirectState;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowState extends SqlObject {

    @SqlUpdate("insert into workflow_state (workflow_state_type_id)"
            + " select workflow_state_type_id from workflow_state_type where workflow_state_type_code = :type")
    @GetGeneratedKeys
    long insert(@Bind("type") StateType type);

    @SqlUpdate("insert into workflow_study_redirect_state (workflow_state_id, study_guid, study_name, redirect_url) "
            + " values (:stateId, :studyGuid, :studyName, :redirectUrl)")
    int insert(@Bind("stateId") long stateId, @Bind("studyGuid") String studyGuid,
               @Bind("studyName") String studyName, @Bind("redirectUrl") String redirectUrl);

    @SqlUpdate("insert into workflow_activity_state (workflow_state_id, study_activity_id, check_each_instance)"
            + " values (:stateId, :activityId, :check)")
    int insertActivityState(@Bind("stateId") long stateId, @Bind("activityId") long activityId, @Bind("check") boolean checkEachInstance);

    @SqlQuery("select ws.workflow_state_id from workflow_state as ws"
            + " join workflow_state_type as wst on wst.workflow_state_type_id = ws.workflow_state_type_id"
            + " where wst.workflow_state_type_code = :type")
    Optional<Long> findIdByType(@Bind("type") StateType type);

    @SqlQuery("select workflow_state_id from workflow_activity_state"
            + " where study_activity_id = :activityId and check_each_instance = :check")
    Optional<Long> findActivityStateId(@Bind("activityId") long activityId, @Bind("check") boolean checkEachInstance);

    @SqlQuery("select * from workflow_study_redirect_state"
            + " where (study_guid = :studyGuid and redirect_url = :redirectUrl)"
            + "    or (study_name = :studyName and redirect_url = :redirectUrl)")
    @RegisterConstructorMapper(StudyRedirectState.class)
    Optional<StudyRedirectState> findByStudyGuidNameAndRedirectUrl(@Bind("studyGuid") String studyGuid,
                                                                   @Bind("studyName") String studyName,
                                                                   @Bind("redirectUrl") String redirectUrl);

}

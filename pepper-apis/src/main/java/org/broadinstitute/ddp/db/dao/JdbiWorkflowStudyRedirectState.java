package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.model.workflow.StudyRedirectState;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiWorkflowStudyRedirectState extends SqlObject {

    @SqlUpdate("insert into workflow_study_redirect_state (workflow_state_id, study_guid, redirect_url) "
            + " values (:stateId, :studyGuid, :redirectUrl)")
    int insert(@Bind("stateId") long stateId, @Bind("studyGuid") String studyGuid, @Bind("redirectUrl") String redirectUrl);

    @SqlQuery("select workflow_state_id, study_guid, redirect_url from workflow_study_redirect_state "
            + " where study_guid = :studyGuid and redirect_url = :redirectUrl")
    @RegisterConstructorMapper(StudyRedirectState.class)
    Optional<StudyRedirectState> findIdByStudyNameGuidAndRedirectUrl(@Bind("studyGuid") String studyGuid,
                                                                     @Bind("redirectUrl") String redirectUrl);

}

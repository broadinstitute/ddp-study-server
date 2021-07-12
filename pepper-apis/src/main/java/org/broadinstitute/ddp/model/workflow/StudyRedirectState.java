package org.broadinstitute.ddp.model.workflow;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyRedirectState implements WorkflowState {

    Long workflowStateId;
    String studyName;
    String studyGuid;
    String redirectUrl;

    @JdbiConstructor
    public StudyRedirectState(@ColumnName("workflow_state_id") Long workflowStateId,
                              @ColumnName("study_guid") String studyGuid,
                              @ColumnName("redirect_url")String redirectUrl) {

        this.workflowStateId = workflowStateId;
        this.studyName = studyName;
        this.studyGuid = studyGuid;
        this.redirectUrl = redirectUrl;
    }

    public StudyRedirectState(String studyName, String studyGuid, String redirectUrl) {
        this.studyName = studyName;
        this.studyGuid = studyGuid;
        this.redirectUrl = redirectUrl;
    }

    public StudyRedirectState(String studyGuid, String redirectUrl) {
        this.studyGuid = studyGuid;
        this.redirectUrl = redirectUrl;
    }

    @Override
    public StateType getType() {
        return StateType.STUDY_REDIRECT;
    }

    @Override
    public boolean matches(WorkflowState other) {
        return false;
    }

    public Long getWorkflowStateId() {
        return workflowStateId;
    }

    public String getStudyName() {
        return studyName;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}

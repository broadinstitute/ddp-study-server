package org.broadinstitute.ddp.model.workflow;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyRedirectState implements WorkflowState {

    long workflowStateId;
    String studyGuid;
    String studyName;
    String redirectUrl;

    @JdbiConstructor
    public StudyRedirectState(@ColumnName("workflow_state_id") long workflowStateId,
                              @ColumnName("study_guid") String studyGuid,
                              @ColumnName("study_name") String studyName,
                              @ColumnName("redirect_url")String redirectUrl) {

        this.workflowStateId = workflowStateId;
        this.studyGuid = studyGuid;
        this.studyName = studyName;
        this.redirectUrl = redirectUrl;
    }

    public StudyRedirectState(String studyGuid, String studyName, String redirectUrl) {
        this.studyGuid = studyGuid;
        this.studyName = studyName;
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

    public long getWorkflowStateId() {
        return workflowStateId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getStudyName() {
        return studyName;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}

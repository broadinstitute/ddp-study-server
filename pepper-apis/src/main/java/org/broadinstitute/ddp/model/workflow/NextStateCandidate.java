package org.broadinstitute.ddp.model.workflow;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.exception.DDPException;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents a potential next state that we can transition to in workflow. The "precondition" PEX expression should be
 * evaluated to determine whether transition is actually allowed or not.
 */
public class NextStateCandidate {

    private long transitionId;
    private StateType nextStateType;
    private Long nextActivityId;
    private Boolean checkEachInstance;
    private String precondition;
    private String studyGuid;
    private String studyName;
    private String redirectUrl;

    @JdbiConstructor
    public NextStateCandidate(
            @ColumnName("workflow_transition_id") long transitionId,
            @ColumnName("workflow_state_type_code") StateType nextStateType,
            @ColumnName("study_activity_id") Long nextActivityId,
            @ColumnName("check_each_instance") Boolean checkEachInstance,
            @ColumnName("study_guid") String studyGuid,
            @ColumnName("study_name") String studyName,
            @ColumnName("redirect_url") String redirectUrl,
            @ColumnName("expression_text") String precondition) {
        this.transitionId = transitionId;
        this.nextStateType = nextStateType;
        this.nextActivityId = nextActivityId;
        this.checkEachInstance = checkEachInstance;
        this.precondition = precondition;
        this.studyGuid = studyGuid;
        this.studyName = studyName;
        this.redirectUrl = redirectUrl;
    }

    public NextStateCandidate(long transitionId, StateType nextStateType, Long nextActivityId, String precondition) {
        this.transitionId = transitionId;
        this.nextStateType = nextStateType;
        this.nextActivityId = nextActivityId;
        this.precondition = precondition;
    }

    public long getTransitionId() {
        return transitionId;
    }

    public StateType getNextStateType() {
        return nextStateType;
    }

    public Long getNextActivityId() {
        return nextActivityId;
    }

    public Boolean getCheckEachInstance() {
        return checkEachInstance;
    }

    public boolean shouldCheckEachInstance() {
        return checkEachInstance != null && checkEachInstance;
    }

    public String getPrecondition() {
        return precondition;
    }

    public boolean hasPrecondition() {
        return StringUtils.isNotBlank(precondition);
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

    public WorkflowState asWorkflowState() {
        if (nextStateType.isStatic()) {
            return StaticState.of(nextStateType);
        } else if (nextStateType == StateType.ACTIVITY) {
            if (nextActivityId == null) {
                throw new DDPException("Workflow activity state is missing an activity id for transition id " + transitionId);
            }
            boolean check = checkEachInstance == null ? false : checkEachInstance;
            return new ActivityState(nextActivityId, check);
        } else if (nextStateType == StateType.STUDY_REDIRECT) {
            return new StudyRedirectState(studyGuid, studyName, redirectUrl);
        } else {
            throw new DDPException("Unhandled workflow state type " + nextStateType);
        }
    }
}

package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;

public class WorkflowStateTrigger extends EventTrigger {
    private Long workflowStateId;
    private Boolean triggerAutomatically;

    public WorkflowStateTrigger(EventConfigurationDto dto) {
        super(dto);
        this.workflowStateId = dto.getWorkflowStateId();
        this.triggerAutomatically = dto.getTriggerAutomatically();
    }

    public Long getWorkflowStateId() {
        return workflowStateId;
    }

    public Boolean triggerAutomatically() {
        return triggerAutomatically;
    }
}

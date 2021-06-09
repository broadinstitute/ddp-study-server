package org.broadinstitute.ddp.model.event;

import static org.broadinstitute.ddp.util.GsonCreateUtil.createJsonIgnoreNulls;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.event.publish.EventPublisher;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

/**
 * Event {@link EventActionType#UPDATE_CUSTOM_WORKFLOW} handler.
 * It publishes the event to other consumers (with using an instance of
 * {@link EventPublisher} passed in the constructor.
 * A default implementation of a publisher sends a message to a specified pubsub-topic
 * (to be consumed by DSM).
 */
public class UpdateCustomWorkflowEventAction extends EventAction {

    public static final String PAYLOAD_FIELD__WORKFLOW = "workflow";
    public static final String PAYLOAD_FIELD__STATUS = "status";

    private final EventPublisher eventPublisher;
    private final String workflow;
    private final String status;

    public UpdateCustomWorkflowEventAction(
            EventConfiguration eventConfiguration,
            EventConfigurationDto dto,
            EventPublisher eventPublisher) {
        super(eventConfiguration, dto);
        this.workflow = dto.getCustomWorkflowName();
        this.status = dto.getCustomWorkflowStatus();
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal signal) {
        eventPublisher.publishEvent(
                EventActionType.UPDATE_CUSTOM_WORKFLOW.name(),
                generatePayload(workflow, status),
                signal.getStudyGuid(),
                signal.getParticipantGuid()
        );
    }

    private String generatePayload(String workflow, String status) {
        return createJsonIgnoreNulls(
                PAYLOAD_FIELD__WORKFLOW, workflow,
                PAYLOAD_FIELD__STATUS, status);
    }
}

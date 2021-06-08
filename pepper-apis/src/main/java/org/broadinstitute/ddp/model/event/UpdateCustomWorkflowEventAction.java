package org.broadinstitute.ddp.model.event;

import static org.broadinstitute.ddp.util.GsonCreateUtil.createJsonIgnoreNulls;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.event.publish.EventActionPublisher;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

/**
 * Event {@link EventActionType#UPDATE_CUSTOM_WORKFLOW} handler.
 * It publishes the event to other consumers (with using an instance of
 * {@link EventActionPublisher} passed in the constructor.
 * A default implementation of a publisher sends a message to a specified pubsub-topic
 * (to be consumed by DSM).
 */
public class UpdateCustomWorkflowEventAction extends EventAction {

    public static final String PAYLOAD_FIELD__WORKFLOW = "workflow";
    public static final String PAYLOAD_FIELD__STATUS = "status";

    private static final EventActionType EVENT = EventActionType.UPDATE_CUSTOM_WORKFLOW;

    private final EventActionPublisher eventActionPublisher;
    private final String workflow;
    private final String status;

    public UpdateCustomWorkflowEventAction(
            EventConfiguration eventConfiguration,
            EventConfigurationDto dto,
            EventActionPublisher eventActionPublisher) {
        super(eventConfiguration, dto);
        this.workflow = dto.getCustomWorkflowName();
        this.status = dto.getCustomWorkflowStatus();
        this.eventActionPublisher = eventActionPublisher;
    }

    @Override
    public void doAction(PexInterpreter pexInterpreter, Handle handle, EventSignal signal) {
        eventActionPublisher.publishEventAction(
                EVENT,
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

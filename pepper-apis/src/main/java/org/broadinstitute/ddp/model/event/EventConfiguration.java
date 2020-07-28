package org.broadinstitute.ddp.model.event;

import org.broadinstitute.ddp.db.dto.EventConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.jdbi.v3.core.Handle;

public class EventConfiguration {
    private long eventConfigurationId;
    private EventActionType eventActionType;
    private EventTriggerType eventTriggerType;
    private EventTrigger eventTrigger;
    private EventAction eventAction;
    private String preconditionExpression;
    private String cancelExpression;
    private Integer maxOccurrencesPerUser;
    private boolean dispatchToHousekeeping;
    private Integer postDelaySeconds;

    public EventConfiguration(EventConfigurationDto dto) {
        this.eventConfigurationId = dto.getEventConfigurationId();
        this.eventActionType = dto.getEventActionType();
        this.eventTriggerType = dto.getEventTriggerType();
        this.preconditionExpression = dto.getPreconditionExpression();
        this.cancelExpression = dto.getCancelExpression();
        this.maxOccurrencesPerUser = dto.getMaxOccurrencesPerUser();
        this.dispatchToHousekeeping = dto.dispatchToHousekeeping();
        this.postDelaySeconds = dto.getPostDelaySeconds();

        switch (eventTriggerType) {
            case ACTIVITY_STATUS:
                eventTrigger = new ActivityStatusChangeTrigger(dto);
                break;
            case WORKFLOW_STATE:
                eventTrigger = new WorkflowStateTrigger(dto);
                break;
            case DSM_NOTIFICATION:
                eventTrigger = new DsmNotificationTrigger(dto);
                break;
            case CONSENT_SUSPENDED:
                // No sub-tables
            case GOVERNED_USER_REGISTERED:
                // No sub-tables
            case INVITATION_CREATED:
                // No sub-tables
            case JOIN_MAILING_LIST:
                // No sub-tables
            case KIT_PREP:
                // No sub-tables
            case LOGIN_ACCOUNT_CREATED:
                // No sub-tables
            case MEDICAL_UPDATE:
                // No sub-tables
            case REACHED_AOM:
                // No sub-tables
            case REACHED_AOM_PREP:
                // No sub-tables
            case USER_NOT_IN_STUDY:
                // No sub-tables
            case USER_REGISTERED:
                // No sub-tables
            case EXIT_REQUEST:
                // The simple case
                eventTrigger = new EventTrigger(dto);
                break;
            default:
                throw new DDPException("Trigger type: " + eventTriggerType.name() + " is not properly configured in "
                        + "the EventConfiguration ctor");
        }

        switch (eventActionType) {
            case ACTIVITY_INSTANCE_CREATION:
                eventAction = new ActivityInstanceCreationEventAction(this, dto);
                break;
            case NOTIFICATION:
                eventAction = new NotificationEventAction(this, dto);
                break;
            case USER_ENROLLED:
                eventAction = new EnrollUserEventAction(this, dto);
                break;
            case ANNOUNCEMENT:
                eventAction = new AnnouncementEventAction(this, dto);
                break;
            case CREATE_INVITATION:
                eventAction = new CreateInvitationEventAction(this, dto);
                break;
            case COPY_ANSWER:
                eventAction = new CopyAnswerEventAction(this, dto);
                break;
            case HIDE_ACTIVITIES:
                eventAction = new HideActivitiesEventAction(this, dto);
                break;
            case MARK_ACTIVITIES_READ_ONLY:
                eventAction = new MarkActivitiesReadOnlyEventAction(this, dto);
                break;
            case PDF_GENERATION:
                eventAction = new PdfGenerationEventAction(this, dto);
                break;
            case REVOKE_PROXIES:
                eventAction = new RevokeProxiesEventAction(this, dto);
                break;
            default:
                throw new DDPException("Event action type: " + eventActionType.name() + " is not properly configured in "
                        + "the EventConfiguration ctor");

        }

    }

    public void doAction(PexInterpreter treeWalkInterpreter, Handle handle, EventSignal eventSignal) {
        eventAction.doAction(treeWalkInterpreter, handle, eventSignal);
    }

    public boolean isTriggered(Handle handle, EventSignal eventSignal) {
        return eventTrigger.isTriggered(handle, eventSignal);
    }

    public void setEventTrigger(EventTrigger eventTrigger) {
        this.eventTrigger = eventTrigger;
    }

    public void setEventAction(EventAction eventAction) {
        this.eventAction = eventAction;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public EventActionType getEventActionType() {
        return eventActionType;
    }

    public EventTriggerType getEventTriggerType() {
        return eventTriggerType;
    }

    public EventTrigger getEventTrigger() {
        return eventTrigger;
    }

    public EventAction getEventAction() {
        return eventAction;
    }

    public String getPreconditionExpression() {
        return preconditionExpression;
    }

    public String getCancelExpression() {
        return cancelExpression;
    }

    public Integer getMaxOccurrencesPerUser() {
        return maxOccurrencesPerUser;
    }

    public boolean dispatchToHousekeeping() {
        return dispatchToHousekeeping;
    }

    public Integer getPostDelaySeconds() {
        return postDelaySeconds;
    }

    @Override
    public String toString() {
        return "EventConfiguration{"
                + "eventConfigurationId=" + eventConfigurationId
                + ", eventActionType=" + eventActionType
                + ", eventTriggerType=" + eventTriggerType
                + ", preconditionExpression='" + preconditionExpression + '\''
                + ", cancelExpression='" + cancelExpression + '\''
                + ", maxOccurrencesPerUser=" + maxOccurrencesPerUser
                + ", dispatchToHousekeeping=" + dispatchToHousekeeping
                + ", postDelaySeconds=" + postDelaySeconds
                + '}';
    }
}

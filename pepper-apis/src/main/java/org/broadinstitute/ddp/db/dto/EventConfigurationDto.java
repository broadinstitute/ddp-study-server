package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;

/**
 * A DTO representing the event configuration, left joined with possible trigger and action configuration info
 */
public class EventConfigurationDto {
    private long eventConfigurationId;
    private EventTriggerType eventTriggerType;
    private EventActionType eventActionType;
    private Integer postDelaySeconds;
    private boolean dispatchToHousekeeping;
    private String preconditionExpression;
    private String cancelExpression;
    private Integer maxOccurrencesPerUser;
    private String gcpTopic; // FIXME should not be a base field of EventAction

    /**
     * Triggers
     **/
    /* ACTIVITY_STATUS */
    private InstanceStatusType instanceStatusType;
    private Long activityStatusTriggerStudyActivityId;

    /* WORKFLOW_STATE */
    private Long workflowStateId;
    private Boolean triggerAutomatically;

    /* JOIN_MAILING_LIST */
    // No sub-table

    /* DSM_NOTIFICATION */
    private String dsmNotificationEventType; // FIXME Turn into real enum

    /* MEDICAL_UPDATE */
    // No sub-table

    /* USER_NOT_IN_STUDY */
    // No sub-table

    /* USER_REGISTERED */
    // No sub-table

    /* EXIT_REQUEST */
    // No sub-table

    /**
     * Actions
     **/
    /* ANNOUNCEMENT */
    private Long messageTemplateId;

    /* NOTIFICATION */
    private NotificationType notificationType;
    private NotificationServiceType notificationServiceType;
    private Long notificationTemplateId;
    private Long linkedActivityId;

    /* PDF_GENERATION */
    // A list of Tuple<pdfDocumentConfigurationId, generateIfMissing>
    private List<Pair<Long, Boolean>> pdfConfigs = new ArrayList<>();

    /* ACTIVITY_INSTANCE_CREATION */
    private Long activityInstanceCreationStudyActivityId;

    /* USER_ENROLLED */
    // No sub-table

    /* COPY_ANSWER */
    private CopyAnswerTarget copyAnswerTarget;
    private String copySourceQuestionStableId;

    public EventConfigurationDto(long eventConfigurationId,
                                 EventTriggerType eventTriggerType,
                                 EventActionType eventActionType,
                                 int postDelaySeconds,
                                 boolean dispatchToHousekeeping,
                                 String preconditionExpression,
                                 String cancelExpression,
                                 Integer maxOccurrencesPerUser,
                                 String gcpTopic,
                                 InstanceStatusType instanceStatusType,
                                 Long activityStatusTriggerStudyActivityId,
                                 Long workflowStateId,
                                 Boolean triggerAutomatically,
                                 String dsmNotificationEventType,
                                 Long messageTemplateId,
                                 NotificationType notificationType,
                                 NotificationServiceType notificationServiceType,
                                 Long notificationTemplateId,
                                 Long linkedActivityId,
                                 Long activityInstanceCreationStudyActivityId,
                                 CopyAnswerTarget copyAnswerTarget,
                                 String copySourceQuestionStableId) {
        this.eventConfigurationId = eventConfigurationId;
        this.eventTriggerType = eventTriggerType;
        this.eventActionType = eventActionType;
        this.postDelaySeconds = postDelaySeconds;
        this.dispatchToHousekeeping = dispatchToHousekeeping;
        this.preconditionExpression = preconditionExpression;
        this.cancelExpression = cancelExpression;
        this.maxOccurrencesPerUser = maxOccurrencesPerUser;
        this.gcpTopic = gcpTopic;
        this.instanceStatusType = instanceStatusType;
        this.activityStatusTriggerStudyActivityId = activityStatusTriggerStudyActivityId;
        this.workflowStateId = workflowStateId;
        this.triggerAutomatically = triggerAutomatically;
        this.dsmNotificationEventType = dsmNotificationEventType;
        this.messageTemplateId = messageTemplateId;
        this.notificationType = notificationType;
        this.notificationServiceType = notificationServiceType;
        this.notificationTemplateId = notificationTemplateId;
        this.linkedActivityId = linkedActivityId;
        this.activityInstanceCreationStudyActivityId = activityInstanceCreationStudyActivityId;
        this.copyAnswerTarget = copyAnswerTarget;
        this.copySourceQuestionStableId = copySourceQuestionStableId;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public EventTriggerType getEventTriggerType() {
        return eventTriggerType;
    }

    public EventActionType getEventActionType() {
        return eventActionType;
    }

    public int getPostDelaySeconds() {
        return postDelaySeconds;
    }

    public boolean dispatchToHousekeeping() {
        return dispatchToHousekeeping;
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

    public String getGcpTopic() {
        return gcpTopic;
    }

    public InstanceStatusType getInstanceStatusType() {
        return instanceStatusType;
    }

    public Long getActivityStatusTriggerStudyActivityId() {
        return activityStatusTriggerStudyActivityId;
    }

    public Long getWorkflowStateId() {
        return workflowStateId;
    }

    public Boolean getTriggerAutomatically() {
        return triggerAutomatically;
    }

    public String getDsmNotificationEventType() {
        return dsmNotificationEventType;
    }

    public Long getMessageTemplateId() {
        return messageTemplateId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationServiceType;
    }

    public Long getNotificationTemplateId() {
        return notificationTemplateId;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public Long getActivityInstanceCreationStudyActivityId() {
        return activityInstanceCreationStudyActivityId;
    }

    public CopyAnswerTarget getCopyAnswerTarget() {
        return copyAnswerTarget;
    }

    public String getCopySourceQuestionStableId() {
        return copySourceQuestionStableId;
    }

    public void addPdfConfig(Long pdfDocumentConfigurationId, Boolean generateIfMissing) {
        pdfConfigs.add(new ImmutablePair<>(pdfDocumentConfigurationId, generateIfMissing));
    }

    public List<Pair<Long, Boolean>> getPdfConfigs() {
        return pdfConfigs;
    }
}

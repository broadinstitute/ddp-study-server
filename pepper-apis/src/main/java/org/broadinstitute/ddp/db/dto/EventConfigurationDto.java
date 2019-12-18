package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.CopyAnswerTarget;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.customizer.Bind;


public class EventConfigurationDto {
    private EventTriggerType triggerType;
    private Integer secondsToWaitBeforePosting;
    private long eventConfigurationId;
    private String preconditionExpression;
    private String cancelExpression;
    private long activityIdToCreate;
    private Integer maxInstancesPerUser;
    private Integer maxOccurrencesPerUser;
    private EventActionType eventActionType;
    private long announcementMsgTemplateId;
    private boolean announcementIsPermanent;
    private boolean announcementCreateForProxies;
    private CopyAnswerTarget copyAnswerTarget;
    private String copySourceQuestionStableId;

    @JdbiConstructor
    public EventConfigurationDto(@Bind("triggerType") EventTriggerType triggerType,
                                 @Bind("secondsToWaitBeforePosting") Integer secondsToWaitBeforePosting,
                                 @Bind("eventConfigurationId") long eventConfigurationId,
                                 @Bind("preconditionExpression") String preconditionExpression,
                                 @Bind("cancelExpression") String cancelExpression,
                                 @Bind("activityIdToCreate") long activityIdToCreate,
                                 @Bind("maxInstancesPerUser") Integer maxInstancesPerUser,
                                 @Bind("maxOccurrencesPerUser") Integer maxOccurrencesPerUser,
                                 @Bind("eventActionType") EventActionType eventActionType,
                                 @Bind("announcementMsgTemplateId") long announcementMsgTemplateId,
                                 @Bind("announcementIsPermanent") boolean announcementIsPermanent,
                                 @Bind("announcementCreateForProxies") boolean announcementCreateForProxies,
                                 @Bind("copySourceQuestionStableId") String copySourceQuestionStableId,
                                 @Bind("copyAnswerTarget") CopyAnswerTarget copyAnswerTarget) {
        this.triggerType = triggerType;
        this.secondsToWaitBeforePosting = secondsToWaitBeforePosting;
        this.eventConfigurationId = eventConfigurationId;
        this.preconditionExpression = preconditionExpression;
        this.cancelExpression = cancelExpression;
        this.activityIdToCreate = activityIdToCreate;
        this.maxInstancesPerUser = maxInstancesPerUser;
        this.maxOccurrencesPerUser = maxOccurrencesPerUser;
        this.eventActionType = eventActionType;
        this.announcementMsgTemplateId = announcementMsgTemplateId;
        this.announcementIsPermanent = announcementIsPermanent;
        this.announcementCreateForProxies = announcementCreateForProxies;
        this.copySourceQuestionStableId = copySourceQuestionStableId;
        this.copyAnswerTarget = copyAnswerTarget;
    }

    /**
     * Creates a new event configuration
     *
     * @param eventTriggerType           the type of trigger
     * @param secondsToWaitBeforePosting how many seconds to wait (at least) before publishing the event
     * @param eventConfigurationId       reference to the row id
     */
    public EventConfigurationDto(EventTriggerType eventTriggerType,
                                 Integer secondsToWaitBeforePosting,
                                 long eventConfigurationId,
                                 EventActionType eventActionType) {
        this.triggerType = eventTriggerType;
        this.secondsToWaitBeforePosting = secondsToWaitBeforePosting;
        this.eventConfigurationId = eventConfigurationId;
        this.eventActionType = eventActionType;
    }

    public EventTriggerType getTriggerType() {
        return triggerType;
    }

    public Integer getSecondsToWaitBeforePosting() {
        return secondsToWaitBeforePosting;
    }

    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public String getPreconditionExpression() {
        return preconditionExpression;
    }

    public String getCancelExpression() {
        return cancelExpression;
    }

    public long getActivityIdToCreate() {
        return activityIdToCreate;
    }

    public Integer getMaxInstancesPerUser() {
        return maxInstancesPerUser;
    }

    public Integer getMaxOccurrencesPerUser() {
        return maxOccurrencesPerUser;
    }

    public EventActionType getEventActionType() {
        return eventActionType;
    }

    public long getAnnouncementMsgTemplateId() {
        return announcementMsgTemplateId;
    }

    public boolean getAnnouncementIsPermanent() {
        return announcementIsPermanent;
    }

    public boolean getAnnouncementCreateForProxies() {
        return announcementCreateForProxies;
    }

    public CopyAnswerTarget getCopyAnswerTarget() {
        return copyAnswerTarget;
    }

    public String getCopySourceQuestionStableId() {
        return copySourceQuestionStableId;
    }
}

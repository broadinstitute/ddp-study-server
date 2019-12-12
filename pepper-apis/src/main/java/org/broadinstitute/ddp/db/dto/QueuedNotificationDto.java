package org.broadinstitute.ddp.db.dto;

import java.util.Collection;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;

public class QueuedNotificationDto extends QueuedEventDto {

    private NotificationDetailsDto notificationDetailsDto;

    public QueuedNotificationDto(QueuedEventDto pendingEvent,
                                 NotificationDetailsDto notificationDetails) {
        super(pendingEvent.getEventConfigurationId(),
                pendingEvent.getQueuedEventId(),
                pendingEvent.getOperatorUserId(),
                pendingEvent.getParticipantGuid(),
                pendingEvent.getParticipantHruid(),
                pendingEvent.getActionType(),
                pendingEvent.getHousekeepingVersion(),
                pendingEvent.getMaxOccurrencesPerUser(),
                pendingEvent.getPubSubTopic(),
                pendingEvent.getPrecondition(),
                pendingEvent.getCancelCondition(),
                pendingEvent.getStudyGuid());
        this.notificationDetailsDto = notificationDetails;
    }

    public NotificationType getNotificationType() {
        return notificationDetailsDto.getNotificationType();
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationDetailsDto.getNotificationServiceType();
    }

    public String getApiKey() {
        return notificationDetailsDto.getApiKey();
    }

    public String getStudyFromName() {
        return notificationDetailsDto.getStudyFromName();
    }

    public String getStudyFromEmail() {
        return notificationDetailsDto.getStudyFromEmail();
    }

    public String getDefaultSalutation() {
        return notificationDetailsDto.getDefaultSalutation();
    }

    public String getTemplateKey() {
        return notificationDetailsDto.getTemplateKey();
    }

    public Long getLinkedActivityId() {
        return notificationDetailsDto.getLinkedActivityId();
    }

    public String getUserFirstName() {
        return notificationDetailsDto.getUserFirstName();
    }

    public String getUserLastName() {
        return notificationDetailsDto.getUserLastName();
    }

    public Collection<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return notificationDetailsDto.getTemplateSubstitutions();
    }

    public void addTemplateSubstitutions(NotificationTemplateSubstitutionDto... substitutions) {
        for (NotificationTemplateSubstitutionDto substitution : substitutions) {
            notificationDetailsDto.getTemplateSubstitutions().add(substitution);
        }
    }

    public String getWebBaseUrl() {
        return notificationDetailsDto.getWebBaseUrl();
    }

    public String getToEmail() {
        return notificationDetailsDto.getToEmailAddress();
    }
}

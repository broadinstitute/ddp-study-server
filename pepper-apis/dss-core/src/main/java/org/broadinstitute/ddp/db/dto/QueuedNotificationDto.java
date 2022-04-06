package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import java.util.List;
import java.util.stream.Stream;

public final class QueuedNotificationDto extends QueuedEventDto {
    private final NotificationDetailsDto notificationDetailsDto;

    @JdbiConstructor
    public QueuedNotificationDto(
            @Nested QueuedEventDto pendingEvent,
            @Nested NotificationDetailsDto notificationDetails) {
        super(pendingEvent);
        this.notificationDetailsDto = notificationDetails;
    }

    public NotificationType getNotificationType() {
        return notificationDetailsDto.getNotificationType();
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationDetailsDto.getServiceType();
    }

    public Long getLinkedActivityId() {
        return notificationDetailsDto.getLinkedActivityId();
    }

    public String getToEmail() {
        return notificationDetailsDto.getToEmailAddress();
    }

    public String getWebBaseUrl() {
        return notificationDetailsDto.getWebBaseUrl();
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

    public String getParticipantFirstName() {
        return notificationDetailsDto.getParticipantFirstName();
    }

    public String getParticipantLastName() {
        return notificationDetailsDto.getParticipantLastName();
    }

    public List<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return notificationDetailsDto.getTemplateSubstitutions();
    }

    public void addTemplateSubstitutions(NotificationTemplateSubstitutionDto... substitutions) {
        Stream.of(substitutions).forEach(notificationDetailsDto::addTemplateSubstitution);
    }
}

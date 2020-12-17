package org.broadinstitute.ddp.db.dto;

import java.util.Collection;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class QueuedNotificationDto extends QueuedEventDto {

    private NotificationDetailsDto notificationDetailsDto;

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

    public String getStaffEmail() {
        return notificationDetailsDto.getStaffEmail();
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

    public Collection<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return notificationDetailsDto.getTemplateSubstitutions();
    }

    public Collection<Long> getAttachmentIds() {
        return notificationDetailsDto.getAttachmentIds();
    }

    public void addTemplateSubstitutions(NotificationTemplateSubstitutionDto... substitutions) {
        for (NotificationTemplateSubstitutionDto substitution : substitutions) {
            notificationDetailsDto.addTemplateSubstitution(substitution);
        }
    }

    public void addAttachments(Long... attachmentIds) {
        for (Long attachmentId : attachmentIds) {
            notificationDetailsDto.addAttachmentId(attachmentId);
        }
    }
}

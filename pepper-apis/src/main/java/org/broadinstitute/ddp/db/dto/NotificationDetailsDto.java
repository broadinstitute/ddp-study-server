package org.broadinstitute.ddp.db.dto;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationDetailsDto {

    private NotificationType notificationType;
    private NotificationServiceType serviceType;
    private Long linkedActivityId;
    private String toEmailAddress;
    private String webBaseUrl;
    private String apiKey;
    private String studyFromName;
    private String studyFromEmail;
    private String staffEmail;
    private String defaultSalutation;
    private String participantFirstName;
    private String participantLastName;
    private Set<NotificationTemplateSubstitutionDto> templateSubstitutions = new HashSet<>();
    private Set<Long> attachmentIds = new HashSet<>();

    @JdbiConstructor
    public NotificationDetailsDto(
            @ColumnName("notification_type") NotificationType notificationType,
            @ColumnName("service_type") NotificationServiceType serviceType,
            @ColumnName("linked_activity_id") Long linkedActivityId,
            @ColumnName("to_email_address") String toEmailAddress,
            @ColumnName("study_web_base_url") String webBaseUrl,
            @ColumnName("sendgrid_api_key") String apiKey,
            @ColumnName("sendgrid_from_name") String studyFromName,
            @ColumnName("sendgrid_from_email") String studyFromEmail,
            @ColumnName("sendgrid_staff_email") String staffEmail,
            @ColumnName("sendgrid_default_salutation") String defaultSalutation,
            @ColumnName("participant_first_name") String participantFirstName,
            @ColumnName("participant_last_name") String participantLastName) {
        this.notificationType = notificationType;
        this.serviceType = serviceType;
        this.linkedActivityId = linkedActivityId;
        this.toEmailAddress = toEmailAddress;
        this.webBaseUrl = webBaseUrl;
        this.apiKey = apiKey;
        this.studyFromName = studyFromName;
        this.studyFromEmail = studyFromEmail;
        this.staffEmail = staffEmail;
        this.defaultSalutation = defaultSalutation;
        this.participantFirstName = participantFirstName;
        this.participantLastName = participantLastName;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getServiceType() {
        return serviceType;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public String getToEmailAddress() {
        return toEmailAddress;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getStudyFromName() {
        return studyFromName;
    }

    public String getStudyFromEmail() {
        return studyFromEmail;
    }

    public String getStaffEmail() {
        return staffEmail;
    }

    public String getDefaultSalutation() {
        return defaultSalutation;
    }

    public String getParticipantFirstName() {
        return participantFirstName;
    }

    public String getParticipantLastName() {
        return participantLastName;
    }

    public Set<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return templateSubstitutions;
    }

    public Collection<Long> getAttachmentIds() {
        return attachmentIds;
    }

    public void addTemplateSubstitution(NotificationTemplateSubstitutionDto substitution) {
        if (substitution != null) {
            templateSubstitutions.add(substitution);
        }
    }

    public void addAttachmentId(Long attachmentId) {
        if (attachmentId != null) {
            attachmentIds.add(attachmentId);
        }
    }
}

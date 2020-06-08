package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationDetailsDto {

    private NotificationType notificationType;
    private NotificationServiceType serviceType;
    private String templateKey;
    private boolean isDynamicTemplate;
    private Long linkedActivityId;
    private String toEmailAddress;
    private String webBaseUrl;
    private String apiKey;
    private String studyFromName;
    private String studyFromEmail;
    private String defaultSalutation;
    private String participantFirstName;
    private String participantLastName;
    private List<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();

    @JdbiConstructor
    public NotificationDetailsDto(
            @ColumnName("notification_type") NotificationType notificationType,
            @ColumnName("service_type") NotificationServiceType serviceType,
            @ColumnName("template_key") String templateKey,
            @ColumnName("is_dynamic") boolean isDynamic,
            @ColumnName("linked_activity_id") Long linkedActivityId,
            @ColumnName("to_email_address") String toEmailAddress,
            @ColumnName("study_web_base_url") String webBaseUrl,
            @ColumnName("sendgrid_api_key") String apiKey,
            @ColumnName("sendgrid_from_name") String studyFromName,
            @ColumnName("sendgrid_from_email") String studyFromEmail,
            @ColumnName("sendgrid_default_salutation") String defaultSalutation,
            @ColumnName("participant_first_name") String participantFirstName,
            @ColumnName("participant_last_name") String participantLastName) {
        this.notificationType = notificationType;
        this.serviceType = serviceType;
        this.templateKey = templateKey;
        this.linkedActivityId = linkedActivityId;
        this.toEmailAddress = toEmailAddress;
        this.webBaseUrl = webBaseUrl;
        this.apiKey = apiKey;
        this.studyFromName = studyFromName;
        this.studyFromEmail = studyFromEmail;
        this.defaultSalutation = defaultSalutation;
        this.participantFirstName = participantFirstName;
        this.participantLastName = participantLastName;
        this.isDynamicTemplate = isDynamic;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getServiceType() {
        return serviceType;
    }

    public String getTemplateKey() {
        return templateKey;
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

    public String getDefaultSalutation() {
        return defaultSalutation;
    }

    public String getParticipantFirstName() {
        return participantFirstName;
    }

    public String getParticipantLastName() {
        return participantLastName;
    }

    public boolean isDynamicTemplate() {
        return isDynamicTemplate;
    }

    public List<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return templateSubstitutions;
    }

    public void addTemplateSubstitution(NotificationTemplateSubstitutionDto substitution) {
        if (substitution != null) {
            templateSubstitutions.add(substitution);
        }
    }
}

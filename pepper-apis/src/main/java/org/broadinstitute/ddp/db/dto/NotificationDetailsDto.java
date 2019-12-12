package org.broadinstitute.ddp.db.dto;

import java.util.List;

import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class NotificationDetailsDto {

    private List<NotificationTemplateSubstitutionDto> templateSubstitutions;

    private NotificationType notificationType;

    private NotificationServiceType notificationServiceType;

    private String apiKey;

    private String studyFromName;

    private String studyFromEmail;

    private String defaultSalutation;

    private String templateKey;

    private Long linkedActivityId;

    private String userFirstName;

    private String userLastName;

    private String webBaseUrl;

    private String toEmailAddress;

    @JdbiConstructor
    public NotificationDetailsDto(@ColumnName("notification_type_code") NotificationType notificationType,
                                  @ColumnName("service_code") NotificationServiceType notificationServiceType,
                                  @ColumnName("api_key") String apiKey,
                                  @ColumnName("from_name") String studyFromName,
                                  @ColumnName("from_email") String studyFromEmail,
                                  @ColumnName("defaultSalutation") String defaultSalutation,
                                  @ColumnName("first_name") String userFirstName,
                                  @ColumnName("last_name") String userLastName,
                                  @ColumnName("template_key") String templateKey,
                                  @ColumnName("linked_activity_id") Long linkedActivityId,
                                  @ColumnName("web_base_url") String webBaseUrl,
                                  @ColumnName("email_address") String toEmailAddress) {
        this.notificationType = notificationType;
        this.notificationServiceType = notificationServiceType;
        this.apiKey = apiKey;
        this.studyFromName = studyFromName;
        this.studyFromEmail = studyFromEmail;
        this.defaultSalutation = defaultSalutation;
        this.userFirstName = userFirstName;
        this.userLastName = userLastName;
        this.templateKey = templateKey;
        this.linkedActivityId = linkedActivityId;
        this.webBaseUrl = webBaseUrl;
        this.toEmailAddress = toEmailAddress;
    }

    public void setTemplateSubstitutions(List<NotificationTemplateSubstitutionDto> templateSubstitutions) {
        this.templateSubstitutions = templateSubstitutions;
    }

    public List<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return templateSubstitutions;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public NotificationServiceType getNotificationServiceType() {
        return notificationServiceType;
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

    public String getUserFirstName() {
        return userFirstName;
    }

    public String getUserLastName() {
        return userLastName;
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public Long getLinkedActivityId() {
        return linkedActivityId;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public String getToEmailAddress() {
        return toEmailAddress;
    }
}

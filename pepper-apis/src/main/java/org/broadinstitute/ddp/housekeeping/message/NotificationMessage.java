package org.broadinstitute.ddp.housekeeping.message;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.db.dto.NotificationTemplateSubstitutionDto;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;

public class NotificationMessage implements HousekeepingMessage {

    @SerializedName("notificationType")
    private NotificationType notificationType;

    @SerializedName("notificationService")
    private NotificationServiceType notificationService;

    @SerializedName("templateKey")
    private String templateKey;

    @SerializedName("distributionList")
    private Collection<String> distributionList;

    @SerializedName("participantFirstName")
    private String participantFirstName;

    @SerializedName("participantLastName")
    private String participantLastName;

    @SerializedName("participantGuid")
    private String participantGuid;

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("fromName")
    private String fromName;

    @SerializedName("fromEmail")
    private String fromEmail;

    @SerializedName("apiKey")
    private String apiKey;

    @SerializedName("defaultSalutation")
    private String defaultSalutation;

    @SerializedName("templateSubstitutions")
    private Collection<NotificationTemplateSubstitutionDto> templateSubstitutions = Collections.emptyList();

    @SerializedName("webBaseUrl")
    private String webBaseUrl;

    @SerializedName("eventConfigId")
    private long eventConfigurationId;

    @SerializedName("attachmentIds")
    private Collection<Long> attachmentIds;

    private boolean isDynamicTemplate;

    public NotificationMessage(NotificationType notificationType,
                               NotificationServiceType service,
                               String templateKey,
                               boolean isDynamicTemplate,
                               Collection<String> sendToList,
                               String participantFirstName,
                               String participantLastName,
                               String participantGuid,
                               String studyGuid, String fromName,
                               String fromEmail,
                               String apiKey,
                               String defaultSalutation,
                               Collection<NotificationTemplateSubstitutionDto> templateSubstitutions,
                               String webBaseUrl,
                               long eventConfigurationId,
                               Collection<Long> attachmentsIds) {
        this.notificationType = notificationType;
        this.notificationService = service;
        this.templateKey = templateKey;
        this.isDynamicTemplate = isDynamicTemplate;
        this.distributionList = sendToList;
        this.participantFirstName = participantFirstName;
        this.participantLastName = participantLastName;
        this.participantGuid = participantGuid;
        this.studyGuid = studyGuid;
        this.fromName = fromName;
        this.fromEmail = fromEmail;
        this.apiKey = apiKey;
        this.defaultSalutation = defaultSalutation;
        if (templateSubstitutions != null) {
            this.templateSubstitutions = templateSubstitutions;
        }
        this.webBaseUrl = webBaseUrl;
        this.eventConfigurationId = eventConfigurationId;
        this.attachmentIds = attachmentsIds;
    }

    @Override
    public long getEventConfigurationId() {
        return eventConfigurationId;
    }

    public NotificationType getNotificationType() {
        return notificationType;
    }

    public Collection<NotificationTemplateSubstitutionDto> getTemplateSubstitutions() {
        return templateSubstitutions;
    }

    public Optional<String> getTemplateSubstitutionValue(String varName) {
        return this.templateSubstitutions.stream().filter(ts -> varName.equals(ts.getVariableName())).findFirst().map(ts -> ts.getValue());
    }

    public String getTemplateKey() {
        return templateKey;
    }

    public Collection<String> getDistributionList() {
        return distributionList;
    }

    public String getParticipantFirstName() {
        return participantFirstName;
    }

    public String getParticipantLastName() {
        return participantLastName;
    }

    public String getParticipantGuid() {
        return participantGuid;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public String getFromName() {
        return fromName;
    }

    public String getFromEmail() {
        return fromEmail;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getDefaultSalutation() {
        return defaultSalutation;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public boolean isDynamicTemplate() {
        return isDynamicTemplate;
    }

    public Collection<Long> getAttachmentIds() {
        return attachmentIds;
    }
}

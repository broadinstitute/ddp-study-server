package org.broadinstitute.ddp.db.dto;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.broadinstitute.ddp.model.event.NotificationServiceType;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@RequiredArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class NotificationDetailsDto {
    @ColumnName("notification_type")
    NotificationType notificationType;

    @ColumnName("service_type")
    NotificationServiceType serviceType;

    @ColumnName("linked_activity_id")
    Long linkedActivityId;

    @ColumnName("to_email_address")
    String toEmailAddress;

    @ColumnName("study_web_base_url")
    String webBaseUrl;

    @ColumnName("sendgrid_api_key")
    String apiKey;

    @ColumnName("sendgrid_from_name")
    String studyFromName;

    @ColumnName("sendgrid_from_email")
    String studyFromEmail;

    @ColumnName("sendgrid_default_salutation")
    String defaultSalutation;

    @ColumnName("participant_first_name")
    String participantFirstName;

    @ColumnName("participant_last_name")
    String participantLastName;
    
    List<NotificationTemplateSubstitutionDto> templateSubstitutions = new ArrayList<>();

    public void addTemplateSubstitution(NotificationTemplateSubstitutionDto substitution) {
        if (substitution != null) {
            templateSubstitutions.add(substitution);
        }
    }
}

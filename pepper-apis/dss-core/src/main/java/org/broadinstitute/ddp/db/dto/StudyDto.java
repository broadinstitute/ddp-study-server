package org.broadinstitute.ddp.db.dto;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.SuperBuilder;
import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

import javax.annotation.Nullable;
import java.io.Serializable;

@Value
@SuperBuilder(toBuilder = true)
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class StudyDto implements Serializable {
    @ColumnName("umbrella_study_id")
    long id;

    @ColumnName("guid")
    String guid;

    @ColumnName("study_name")
    String name;

    @ColumnName("irb_password")
    String irbPassword;

    @ColumnName("web_base_url")
    String webBaseUrl;

    @ColumnName("umbrella_id")
    long umbrellaId;

    @ColumnName("auth0_tenant_id")
    long auth0TenantId;

    @ColumnName("olc_precision_code")
    OLCPrecision olcPrecision;

    @ColumnName("share_participant_location")
    boolean publicDataSharingEnabled;

    @ColumnName("study_email")
    String studyEmail;

    @ColumnName("recaptcha_site_key")
    String recaptchaSiteKey;

    @ColumnName("enable_data_export")
    boolean dataExportEnabled;

    @ColumnName("default_auth0_connection")
    String defaultAuth0Connection;

    @ColumnName("error_present_status_enabled")
    boolean errorPresentStatusEnabled;

    @Nullable
    @ColumnName("notification_email")
    String notificationEmail;

    @Nullable
    @ColumnName("notification_mail_template_id")
    Long notificationMailTemplateId;
}

package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.address.OLCPrecision;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyDto {

    private long id;
    private String guid;
    private String name;
    private String irbPassword;
    private String webBaseUrl;
    private long umbrellaId;
    private long auth0TenantId;
    private OLCPrecision olcPrecision;
    private boolean shareParticipantLocation;
    private String studyEmail;
    private String recaptchaSiteKey;
    private boolean dataExportEnabled;

    @JdbiConstructor
    public StudyDto(@ColumnName("umbrella_study_id") long id,
                    @ColumnName("guid") String guid,
                    @ColumnName("study_name") String name,
                    @ColumnName("irb_password") String irbPassword,
                    @ColumnName("web_base_url") String webBaseUrl,
                    @ColumnName("umbrella_id") long umbrellaId,
                    @ColumnName("auth0_tenant_id") long auth0TenantId,
                    @ColumnName("olc_precision_code") OLCPrecision olcPrecision,
                    @ColumnName("share_participant_location") boolean shareParticipantLocation,
                    @ColumnName("study_email") String studyEmail,
                    @ColumnName("recaptcha_site_key") String recaptchaSiteKey,
                    @ColumnName("enable_data_export") boolean dataExportEnabled) {
        this.id = id;
        this.guid = guid;
        this.name = name;
        this.irbPassword = irbPassword;
        this.webBaseUrl = webBaseUrl;
        this.umbrellaId = umbrellaId;
        this.auth0TenantId = auth0TenantId;
        this.olcPrecision = olcPrecision;
        this.shareParticipantLocation = shareParticipantLocation;
        this.studyEmail = studyEmail;
        this.recaptchaSiteKey = recaptchaSiteKey;
        this.dataExportEnabled = dataExportEnabled;
    }

    public long getId() {
        return id;
    }

    public String getGuid() {
        return guid;
    }

    public String getName() {
        return name;
    }

    public String getIrbPassword() {
        return irbPassword;
    }

    public String getWebBaseUrl() {
        return webBaseUrl;
    }

    public long getUmbrellaId() {
        return umbrellaId;
    }

    public Long getAuth0TenantId() {
        return auth0TenantId;
    }

    public OLCPrecision getOlcPrecision() {
        return olcPrecision;
    }

    public boolean isPublicDataSharingEnabled() {
        return shareParticipantLocation;
    }

    public String getStudyEmail() {
        return studyEmail;
    }

    public boolean isDataExportEnabled() {
        return dataExportEnabled;
    }

    public String getRecaptchaSiteKey() {
        return recaptchaSiteKey;
    }

    public void setRecaptchaSiteKey(String key) {
        this.recaptchaSiteKey = key;
    }
}

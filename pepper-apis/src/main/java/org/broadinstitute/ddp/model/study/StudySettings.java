package org.broadinstitute.ddp.model.study;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudySettings {

    private long studyId;
    private Long inviteErrorTemplateId;
    private boolean analyticsEnabled;
    private String analyticsToken;

    @JdbiConstructor
    public StudySettings(
            @ColumnName("umbrella_study_id") long studyId,
            @ColumnName("invite_error_template_id") Long inviteErrorTemplateId,
            @ColumnName("analytics_enabled") boolean analyticsEnabled,
            @ColumnName("analytics_token") String analyticsToken) {
        this.studyId = studyId;
        this.inviteErrorTemplateId = inviteErrorTemplateId;
        this.analyticsEnabled = analyticsEnabled;
        this.analyticsToken = analyticsToken;
    }

    public long getStudyId() {
        return studyId;
    }

    public Long getInviteErrorTemplateId() {
        return inviteErrorTemplateId;
    }

    public boolean isAnalyticsEnabled() {
        return analyticsEnabled;
    }

    public String getAnalyticsToken() {
        return analyticsToken;
    }

}

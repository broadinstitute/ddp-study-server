package org.broadinstitute.ddp.model.migration;

public class BaseSurvey {

    Long datstatsubmissionId;

    String datstatSessionId;

    String ddpCreated;

    String ddpFirstCompleted;

    String ddpLastUpdated;

    String surveyVersion;

    String surveyStatus;

    String activityVersion; //consent_version translated to activity_version

    Integer datstatSubmissionStatus;

    public BaseSurvey(Long datstatsubmissionId, String datstatSessionId, String ddpCreated, String ddpFirstCompleted,
                      String ddpLastUpdated, String surveyVersion, String activityVersion, String surveyStatus,
                      Integer datstatSubmissionStatus) {
        this.datstatsubmissionId = datstatsubmissionId;
        this.datstatSessionId = datstatSessionId;
        this.ddpCreated = ddpCreated;
        this.ddpFirstCompleted = ddpFirstCompleted;
        this.ddpLastUpdated = ddpLastUpdated;
        this.surveyVersion = surveyVersion;
        this.surveyStatus = surveyStatus;
        this.activityVersion = activityVersion;
        this.datstatSubmissionStatus = datstatSubmissionStatus;
    }

    public Long getDatstatSubmissionId() {
        return datstatsubmissionId;
    }

    public String getDatstatSessionId() {
        return datstatSessionId;
    }

    public String getDdpCreated() {
        return ddpCreated;
    }

    public void setDdpCreated(String ddpCreated) {
        this.ddpCreated = ddpCreated;
    }

    public String getDdpFirstCompleted() {
        return ddpFirstCompleted;
    }

    public String getDdpLastUpdated() {
        return ddpLastUpdated;
    }

    public String getSurveyVersion() {
        return surveyVersion;
    }

    public Integer getDatstatSubmissionStatus() {
        return datstatSubmissionStatus;
    }

    public String getSurveyStatus() {
        return surveyStatus;
    }

    public String getActivityVersion() {
        return activityVersion;
    }
}

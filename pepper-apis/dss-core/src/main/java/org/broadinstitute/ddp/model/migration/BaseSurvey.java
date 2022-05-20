package org.broadinstitute.ddp.model.migration;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public final class BaseSurvey {
    private Long datstatSubmissionId;
    private String datstatSessionId;
    private String ddpCreated;
    private String ddpFirstCompleted;
    private String ddpLastUpdated;
    private String surveyVersion;
    private String activityVersion; //consent_version translated to activity_version
    private String surveyStatus;
    private Integer datstatSubmissionStatus;
}

package org.broadinstitute.lddp.handlers.util;

import lombok.Data;
import lombok.NonNull;

@Data
public class ParticipantSurveyInfo {
    private String participantId;
    private String shortId;
    private String legacyShortId;
    private String survey;
    private String followUpInstance;
    private Long surveyQueued;
    private String surveyStatus;
    private Long triggerId;

    public ParticipantSurveyInfo() {
    }

    public ParticipantSurveyInfo(@NonNull String participantId, @NonNull String survey, @NonNull String followUpInstance,
                                 @NonNull Long surveyQueued,
                                 @NonNull Long triggerId) {
        this.participantId = participantId;
        this.survey = survey;
        this.followUpInstance = followUpInstance;
        this.surveyQueued = surveyQueued;
        this.triggerId = triggerId;
    }
}

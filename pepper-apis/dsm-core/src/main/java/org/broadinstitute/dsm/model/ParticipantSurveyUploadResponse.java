package org.broadinstitute.dsm.model;

import java.util.Collection;

public class ParticipantSurveyUploadResponse {

    private Collection<ParticipantSurveyUploadObject> failedToTrigger;
    private Collection<ParticipantSurveyUploadObject> alreadyTriggered;

    public ParticipantSurveyUploadResponse(Collection<ParticipantSurveyUploadObject> failedToTrigger, Collection<ParticipantSurveyUploadObject> alreadyTriggered) {
        this.failedToTrigger = failedToTrigger;
        this.alreadyTriggered = alreadyTriggered;
    }
}

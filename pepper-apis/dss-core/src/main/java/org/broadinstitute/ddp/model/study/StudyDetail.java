package org.broadinstitute.ddp.model.study;

import com.google.gson.annotations.SerializedName;

public class StudyDetail extends StudySummary {
    @SerializedName("summary")
    private String summary;

    @SerializedName("shouldDisplayLanguageChangePopup")
    private boolean shouldDisplayLanguageChangePopup;

    public StudyDetail(
            String studyGuid,
            String name,
            String summary,
            int registrationCount,
            int participantCount,
            boolean restricted,
            String studyEmail,
            boolean shouldDisplayLanguageChangePopup
    ) {
        super(studyGuid, name, registrationCount, participantCount, restricted, studyEmail);
        this.summary = summary;
        this.shouldDisplayLanguageChangePopup = shouldDisplayLanguageChangePopup;
    }

    public String getSummary() {
        return this.summary;
    }

    public boolean getShouldDisplayLanguageChangePopup() {
        return this.shouldDisplayLanguageChangePopup;
    }
}

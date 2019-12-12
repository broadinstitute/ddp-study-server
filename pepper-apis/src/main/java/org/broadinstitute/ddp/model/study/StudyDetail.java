package org.broadinstitute.ddp.model.study;

import com.google.gson.annotations.SerializedName;

public class StudyDetail extends StudySummary {
    @SerializedName("summary")
    private String summary;
    
    public StudyDetail(
            String studyGuid,
            String name,
            String summary,
            int registrationCount,
            int participantCount,
            boolean restricted,
            String studyEmail
    ) {
        super(studyGuid, name, registrationCount, participantCount, restricted, studyEmail);
        this.summary = summary;
    }

    public String getSummary() {
        return this.summary;
    }
}

package org.broadinstitute.ddp.model.study;

import com.google.gson.annotations.SerializedName;
import org.broadinstitute.ddp.util.MiscUtil;

public class StudySummary {
    @SerializedName("name")
    private String name;

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("participantCount")
    private int participantCount;

    @SerializedName("registeredCount")
    private int registeredCount;

    @SerializedName("restricted")
    private boolean restricted;

    @SerializedName("studyEmail")
    private String studyEmail;

    public StudySummary(
            String studyGuid,
            String name,
            int registrationCount,
            int participantCount,
            boolean restricted,
            String studyEmail
    ) {
        this.studyGuid = MiscUtil.checkNonNull(studyGuid, "studyGuid");
        this.name = MiscUtil.checkNonNull(name, "name");
        this.registeredCount = registrationCount;
        this.participantCount = participantCount;
        this.restricted = restricted;
        this.studyEmail = studyEmail;
    }

    public String getName() {
        return name;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    public int getRegisteredCount() {
        return registeredCount;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public String getStudyEmail() {
        return studyEmail;
    }
}

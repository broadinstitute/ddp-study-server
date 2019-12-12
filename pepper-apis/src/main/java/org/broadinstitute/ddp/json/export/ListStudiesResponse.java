package org.broadinstitute.ddp.json.export;

import com.google.gson.annotations.SerializedName;

public class ListStudiesResponse {

    @SerializedName("studyName")
    private String studyName;

    @SerializedName("studyGuid")
    private String studyGuid;

    @SerializedName("participantCount")
    private Integer participantCount;

    /**
     * Instantiate ListStudiesResponse based on study information and number of participants in study.
     */
    public ListStudiesResponse(String studyName, String studyGuid, Integer participantCount) {
        this.studyName = studyName;
        this.studyGuid = studyGuid;
        this.participantCount = participantCount;
    }

    public String getStudyName() {
        return studyName;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public Integer getParticipantCount() {
        return participantCount;
    }

}

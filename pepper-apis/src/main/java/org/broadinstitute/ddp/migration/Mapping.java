package org.broadinstitute.ddp.migration;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.SerializedName;

/**
 * Represents the top-level mappings configuration file.
 */
class Mapping {

    @SerializedName("study_guid")
    private String studyGuid;
    @SerializedName("participant")
    private MappingParticipant participant;
    @SerializedName("activities")
    private List<MappingActivity> activities = new ArrayList<>();

    public String getStudyGuid() {
        return studyGuid;
    }

    public MappingParticipant getParticipant() {
        return participant;
    }

    public List<MappingActivity> getActivities() {
        return activities;
    }
}

package org.broadinstitute.ddp.model.dsm;

import org.broadinstitute.ddp.model.activity.types.ActivityMappingType;

public class StudyActivityMapping {
    private String studyGuid;
    private ActivityMappingType activityMappingType;
    private long studyActivityId;
    private String subActivityStableId;

    public StudyActivityMapping(String studyGuid,
                                ActivityMappingType activityMappingType,
                                long studyActivityId,
                                String subActivityStableId) {
        this.studyGuid = studyGuid;
        this.activityMappingType = activityMappingType;
        this.studyActivityId = studyActivityId;
        this.subActivityStableId = subActivityStableId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public ActivityMappingType getActivityMappingType() {
        return activityMappingType;
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }

    public String getSubActivityStableId() {
        return subActivityStableId;
    }
}

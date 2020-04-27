package org.broadinstitute.ddp.model.study;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ActivityMapping {

    private ActivityMappingType type;
    private String studyGuid;
    private long activityId;
    private String subActivityStableId;

    @JdbiConstructor
    public ActivityMapping(@ColumnName("study_guid") String studyGuid,
                           @ColumnName("activity_mapping_type") ActivityMappingType type,
                           @ColumnName("activity_id") long activityId,
                           @ColumnName("sub_activity_stable_id") String subActivityStableId) {
        this.studyGuid = studyGuid;
        this.type = type;
        this.activityId = activityId;
        this.subActivityStableId = subActivityStableId;
    }

    public ActivityMappingType getType() {
        return type;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public long getActivityId() {
        return activityId;
    }

    public String getSubActivityStableId() {
        return subActivityStableId;
    }
}

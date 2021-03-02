package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportActivity {
    private long id;
    private long studyActivityId;
    private boolean isIncremental;
    private String activityCode;

    @JdbiConstructor
    public ExportActivity(@ColumnName("export_activity_id") long id,
                          @ColumnName("study_activity_id") long studyActivityId,
                          @ColumnName("is_incremental") boolean isIncremental) {
        this.id = id;
        this.studyActivityId = studyActivityId;
        this.isIncremental = isIncremental;
    }

    public ExportActivity(long studyActivityId, boolean isIncremental) {
        this.studyActivityId = studyActivityId;
        this.isIncremental = isIncremental;
    }

    public ExportActivity(String activityCode, boolean isIncremental) {
        this.activityCode = activityCode;
        this.isIncremental = isIncremental;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public long getId() {
        return id;
    }

    public long getStudyActivityId() {
        return studyActivityId;
    }

    public void setStudyActivityId(long studyActivityId) {
        this.studyActivityId = studyActivityId;
    }

    public boolean isIncremental() {
        return isIncremental;
    }
}


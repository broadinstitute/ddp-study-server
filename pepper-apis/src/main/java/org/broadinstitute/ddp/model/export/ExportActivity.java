package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportActivity {
    private long id;
    private long configuredExportId;
    private String activityCode;
    private long activityId;
    private boolean isIncremental;
    private long studyId;

    @JdbiConstructor
    public ExportActivity(@ColumnName("export_activity_id") long id,
                          @ColumnName("configured_export_id") long configuredExportId,
                          @ColumnName("activity_id") long activityId,
                          @ColumnName("is_incremental") boolean isIncremental) {
        this.id = id;
        this.configuredExportId = configuredExportId;
        this.activityId = activityId;
        this.isIncremental = isIncremental;
    }

    public ExportActivity(long configuredExportId, String activityCode, boolean isIncremental, long studyId) {
        this.configuredExportId = configuredExportId;
        this.activityCode = activityCode;
        this.isIncremental = isIncremental;
        this.studyId = studyId;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public long getId() {
        return id;
    }

    public boolean isIncremental() {
        return isIncremental;
    }

    public long getExportId() {
        return configuredExportId;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getActivityId() {
        return activityId;
    }
}


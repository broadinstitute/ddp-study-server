package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportActivity {
    private long id;
    private long configuredExportId;
    private long studyActivityId;
    private boolean isIncremental;

    @JdbiConstructor
    public ExportActivity(@ColumnName("export_activity_id") long id,
                          @ColumnName("configured_export_id") long configuredExportId,
                          @ColumnName("study_activity_id") long studyActivityId,
                          @ColumnName("is_incremental") boolean isIncremental) {
        this.id = id;
        this.configuredExportId = configuredExportId;
        this.studyActivityId = studyActivityId;
        this.isIncremental = isIncremental;
    }

    public ExportActivity(long configuredExportId, long studyActivityId, boolean isIncremental) {
        this.configuredExportId = configuredExportId;
        this.studyActivityId = studyActivityId;
        this.isIncremental = isIncremental;
    }

    public ExportActivity(boolean isIncremental) {
        this.isIncremental = isIncremental;
    }

    public long getId() {
        return id;
    }

    public long getConfiguredExportId() {
        return configuredExportId;
    }

    public void setConfiguredExportId(long configuredExportId) {
        this.configuredExportId = configuredExportId;
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


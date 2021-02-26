package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExcludedActivityField {

    private long id;
    private long exportActivityId;
    private String excludedActivityField;

    @JdbiConstructor
    public ExcludedActivityField(@ColumnName("excluded_activity_field_id") long id,
                                 @ColumnName("export_activity_id") long exportActivityId,
                                 @ColumnName("excluded_activity_field") String excludedActivityField) {
        this.id = id;
        this.excludedActivityField = excludedActivityField;
        this.exportActivityId = exportActivityId;
    }

    public ExcludedActivityField(long exportActivityId, String excludedActivityField) {
        this.exportActivityId = exportActivityId;
        this.excludedActivityField = excludedActivityField;
    }

    public ExcludedActivityField(String excludedActivityField) {
        this.excludedActivityField = excludedActivityField;
    }

    public long getId() {
        return id;
    }

    public long getExportActivityId() {
        return exportActivityId;
    }

    public void setExportActivityId(long exportActivityId) {
        this.exportActivityId = exportActivityId;
    }

    public String getExcludedActivityField() {
        return excludedActivityField;
    }
}

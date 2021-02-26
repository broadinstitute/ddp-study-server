package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;

public class ExportFirstField {
    private long id;
    private long activityId;
    private String firstField;

    public ExportFirstField(@ColumnName("export_first_field_id") long id,
                            @ColumnName("export_activity_id") long activityId,
                            @ColumnName("export_first_field") String firstField) {
        this.id = id;
        this.activityId = activityId;
        this.firstField = firstField;
    }

    public ExportFirstField(long activityId, String firstField) {
        this.activityId = activityId;
        this.firstField = firstField;
    }

    public ExportFirstField(String firstField) {
        this.firstField = firstField;
    }

    public long getId() {
        return id;
    }

    public long getActivityId() {
        return activityId;
    }

    public void setActivityId(long activityId) {
        this.activityId = activityId;
    }

    public String getFirstField() {
        return firstField;
    }
}

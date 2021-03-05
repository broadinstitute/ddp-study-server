package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExcludedMetadataField {

    private long id;
    private long exportActivityId;
    private final String excludedMetadataField;

    @JdbiConstructor
    public ExcludedMetadataField(@ColumnName("excluded_metadata_field_id") long id,
                                 @ColumnName("export_activity_id") long exportActivityId,
                                 @ColumnName("excluded_metadata_field") String excludedMetadataField) {
        this.id = id;
        this.excludedMetadataField = excludedMetadataField;
        this.exportActivityId = exportActivityId;
    }

    public ExcludedMetadataField(long exportActivityId, String excludedMetadataField) {
        this.excludedMetadataField = excludedMetadataField;
        this.exportActivityId = exportActivityId;
    }

    public ExcludedMetadataField(String excludedMetadataField) {
        this.excludedMetadataField = excludedMetadataField;
    }

    public long getId() {
        return id;
    }

    public long getActivityId() {
        return exportActivityId;
    }

    public String getExcludedMetadataField() {
        return excludedMetadataField;
    }
}

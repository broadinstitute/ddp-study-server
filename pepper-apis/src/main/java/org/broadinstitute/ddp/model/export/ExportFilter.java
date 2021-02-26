package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportFilter {

    private long id;
    private long exportActivityId;
    private long filterTypeId;

    @JdbiConstructor
    public ExportFilter(@ColumnName("export_filter_id") long id,
                        @ColumnName("export_activity_id") long exportActivityId,
                        @ColumnName("export_status_filter_type_id") long filterTypeId) {
        this.id = id;
        this.exportActivityId = exportActivityId;
        this.filterTypeId = filterTypeId;
    }

    public ExportFilter(long exportActivityId, long filterTypeId) {
        this.exportActivityId = exportActivityId;
        this.filterTypeId = filterTypeId;
    }

    public long getId() {
        return id;
    }

    public long getExportActivityId() {
        return exportActivityId;
    }

    public long getFilterTypeId() {
        return filterTypeId;
    }
}

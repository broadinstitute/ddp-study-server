package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportFilter {

    private long id;
    private long exportActivityId;
    private long filterTypeId;
    private String filterType;

    @JdbiConstructor
    public ExportFilter(@ColumnName("export_filter_id") long id,
                        @ColumnName("export_activity_id") long exportActivityId,
                        @ColumnName("export_status_filter_type_id") long filterTypeId) {
        this.id = id;
        this.exportActivityId = exportActivityId;
        this.filterTypeId = filterTypeId;
    }

    public ExportFilter(long exportActivityId, String filterType) {
        this.exportActivityId = exportActivityId;
        this.filterType = filterType;
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

    public String getFilterType() {
        return filterType;
    }
}

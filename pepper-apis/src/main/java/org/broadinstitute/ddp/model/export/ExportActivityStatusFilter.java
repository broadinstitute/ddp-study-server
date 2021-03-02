package org.broadinstitute.ddp.model.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class ExportActivityStatusFilter {
    private long id;
    private long filterId;
    private long statusTypeId;
    private String statusType;

    @JdbiConstructor
    public ExportActivityStatusFilter(@ColumnName("export_activity_status_filter_id") long id,
                                      @ColumnName("export_filter_id") long filterId,
                                      @ColumnName("export_filter_status_type_id") long statusTypeId) {
        this.id = id;
        this.filterId = filterId;
        this.statusTypeId = statusTypeId;
    }

    public ExportActivityStatusFilter(long filterId, String statusType) {
        this.filterId = filterId;
        this.statusType = statusType;
    }

    public ExportActivityStatusFilter(String statusType) {
        this.statusType = statusType;
    }

    public long getId() {
        return id;
    }

    public long getFilterId() {
        return filterId;
    }

    public void setFilterId(long filterId) {
        this.filterId = filterId;
    }

    public long getStatusTypeId() {
        return statusTypeId;
    }

    public String getStatusType() {
        return statusType;
    }
}

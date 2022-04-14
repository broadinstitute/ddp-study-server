package org.broadinstitute.ddp.model.pdf;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public final class PdfActivityDataSource extends PdfDataSource {

    private long activityId;
    private String activityCode;
    private long versionId;
    private String versionTag;

    @JdbiConstructor
    public PdfActivityDataSource(@ColumnName("pdf_data_source_id") long id,
                                 @ColumnName("activity_id") long activityId,
                                 @ColumnName("activity_code") String activityCode,
                                 @ColumnName("activity_version_id") long versionId,
                                 @ColumnName("activity_version_tag") String versionTag) {
        super(id, PdfDataSourceType.ACTIVITY);
        this.activityId = activityId;
        this.activityCode = activityCode;
        this.versionId = versionId;
        this.versionTag = versionTag;
    }

    public PdfActivityDataSource(long activityId, long versionId) {
        super(PdfDataSourceType.ACTIVITY);
        this.activityId = activityId;
        this.versionId = versionId;
    }

    public long getActivityId() {
        return activityId;
    }

    public String getActivityCode() {
        return activityCode;
    }

    public long getVersionId() {
        return versionId;
    }

    public String getVersionTag() {
        return versionTag;
    }
}

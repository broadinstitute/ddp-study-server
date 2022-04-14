package org.broadinstitute.ddp.export;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class DataSyncRequest {

    private long id;
    private long userId;
    private Long studyId;
    private boolean refreshUserEmail;

    @JdbiConstructor
    public DataSyncRequest(@ColumnName("data_sync_request_id") long id,
                           @ColumnName("user_id") long userId,
                           @ColumnName("study_id") Long studyId,
                           @ColumnName("refresh_user_email") boolean refreshUserEmail) {
        this.id = id;
        this.userId = userId;
        this.studyId = studyId;
        this.refreshUserEmail = refreshUserEmail;
    }

    public long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public Long getStudyId() {
        return studyId;
    }

    public boolean isRefreshUserEmail() {
        return refreshUserEmail;
    }
}

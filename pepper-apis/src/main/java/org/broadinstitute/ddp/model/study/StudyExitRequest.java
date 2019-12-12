package org.broadinstitute.ddp.model.study;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class StudyExitRequest {

    private long id;
    private long studyId;
    private long userId;
    private String notes;
    private long createdAt;

    @JdbiConstructor
    public StudyExitRequest(@ColumnName("study_exit_request_id") long id,
                            @ColumnName("study_id") long studyId,
                            @ColumnName("user_id") long userId,
                            @ColumnName("notes") String notes,
                            @ColumnName("created_at") long createdAt) {
        this.id = id;
        this.studyId = studyId;
        this.userId = userId;
        this.notes = notes;
        this.createdAt = createdAt;
    }

    public StudyExitRequest(long studyId, long userId, String notes) {
        this.studyId = studyId;
        this.userId = userId;
        this.notes = notes;
        this.createdAt = Instant.now().toEpochMilli();
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public long getUserId() {
        return userId;
    }

    public String getNotes() {
        return notes;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}

package org.broadinstitute.ddp.db.dto;

import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class RevisionDto {

    private long id;
    private long startMillis;
    private long changedUserId;
    private String changedReason;
    private Long endMillis;
    private Long terminatedUserId;
    private String terminatedReason;

    public static RevisionDto fromStartMetadata(long id, RevisionMetadata meta) {
        return new RevisionDto(id, meta.getTimestamp(), meta.getUserId(), meta.getReason(), null, null, null);
    }

    @JdbiConstructor
    public RevisionDto(long id, long startMillis, long changedUserId, String changedReason,
                       Long endMillis, Long terminatedUserId, String terminatedReason) {
        this.id = id;
        this.startMillis = startMillis;
        this.changedUserId = changedUserId;
        this.changedReason = changedReason;
        this.endMillis = endMillis;
        this.terminatedUserId = terminatedUserId;
        this.terminatedReason = terminatedReason;
    }

    public long getId() {
        return id;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public long getChangedUserId() {
        return changedUserId;
    }

    public String getChangedReason() {
        return changedReason;
    }

    public Long getEndMillis() {
        return endMillis;
    }

    public Long getTerminatedUserId() {
        return terminatedUserId;
    }

    public String getTerminatedReason() {
        return terminatedReason;
    }
}

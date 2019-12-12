package org.broadinstitute.ddp.model.activity.revision;

import java.time.Instant;

/**
 * This class captures metadata about a revisioning change.
 */
public class RevisionMetadata {

    private long timestamp;
    private long userId;
    private String reason;

    public static RevisionMetadata now(long userId, String reason) {
        long millis = Instant.now().toEpochMilli();
        return new RevisionMetadata(millis, userId, reason);
    }

    public RevisionMetadata(long timestamp, long userId, String reason) {
        this.timestamp = timestamp;
        this.userId = userId;
        this.reason = reason;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getUserId() {
        return userId;
    }

    public String getReason() {
        return reason;
    }
}

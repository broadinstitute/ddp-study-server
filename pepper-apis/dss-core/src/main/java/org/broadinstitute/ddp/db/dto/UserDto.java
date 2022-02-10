package org.broadinstitute.ddp.db.dto;

import java.time.Instant;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

public class UserDto {

    private final long userId;
    private final String auth0UserId;
    private final String userGuid;
    private final String userHruid;
    private final String legacyAltPid;
    private final String legacyShortId;
    private final long createdAtMillis;
    private final long updatedAtMillis;
    private final Long expiresAtMillis;

    @JdbiConstructor
    public UserDto(@ColumnName("user_id") long userId,
                   @ColumnName("auth0_user_id") String auth0UserId,
                   @ColumnName("guid") String userGuid,
                   @ColumnName("hruid") String userHruid,
                   @ColumnName("legacy_altpid") String legacyAltPid,
                   @ColumnName("legacy_shortid") String legacyShortId,
                   @ColumnName("created_at") long createdAtMillis,
                   @ColumnName("updated_at") long updatedAtMillis,
                   @ColumnName("expires_at") Long expiresAtMillis) {
        this.userId = userId;
        this.auth0UserId = auth0UserId;
        this.userGuid = userGuid;
        this.userHruid = userHruid;
        this.legacyAltPid = legacyAltPid;
        this.legacyShortId = legacyShortId;
        this.createdAtMillis = createdAtMillis;
        this.updatedAtMillis = updatedAtMillis;
        this.expiresAtMillis = expiresAtMillis;
    }

    public UserDto(long userId, String auth0UserId, String userGuid, String userHruid, String legacyAltPid, String legacyShortId,
                   long createdAtMillis, long updatedAtMillis) {
        this(userId, auth0UserId, userGuid, userHruid, legacyAltPid, legacyShortId, createdAtMillis, updatedAtMillis, null);
    }

    public long getUserId() {
        return userId;
    }

    public String getAuth0UserId() {
        return auth0UserId;
    }

    public String getUserGuid() {
        return userGuid;
    }

    public String getUserHruid() {
        return userHruid;
    }

    public String getLegacyAltPid() {
        return legacyAltPid;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getUpdatedAtMillis() {
        return updatedAtMillis;
    }

    public Long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isTemporary() {
        return expiresAtMillis != null;
    }

    public boolean isExpired() {
        return expiresAtMillis != null && expiresAtMillis <= Instant.now().toEpochMilli();
    }
}

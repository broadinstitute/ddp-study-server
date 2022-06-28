package org.broadinstitute.ddp.db.dto;

import java.time.Instant;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class UserDto {
    @ColumnName("user_id")
    long userId;

    @ColumnName("auth0_user_id")
    String auth0UserId;

    @ColumnName("email")
    String email;

    @ColumnName("guid")
    String userGuid;

    @ColumnName("hruid")
    String userHruid;

    @ColumnName("legacy_altpid")
    String legacyAltPid;

    @ColumnName("legacy_shortid")
    String legacyShortId;

    @ColumnName("created_at")
    long createdAtMillis;

    @ColumnName("updated_at")
    long updatedAtMillis;

    @ColumnName("expires_at")
    Long expiresAtMillis;

    public Optional<String> getAuth0UserId() {
        return Optional.ofNullable(auth0UserId);
    }

    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public boolean isTemporary() {
        return expiresAtMillis != null;
    }

    public boolean isExpired() {
        return expiresAtMillis != null && expiresAtMillis <= Instant.now().toEpochMilli();
    }
}

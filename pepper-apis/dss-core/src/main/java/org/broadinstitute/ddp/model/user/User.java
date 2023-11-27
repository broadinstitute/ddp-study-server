package org.broadinstitute.ddp.model.user;

import java.time.Instant;
import java.util.Optional;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;


/**
 * Representation of a user in the system, with associations to it's profile and address which are considered
 * system-wide resources.
 */
@Data
public class User {

    public static final String METADATA_FIRST_NAME = "first_name";
    public static final String METADATA_LAST_NAME = "last_name";
    public static final String METADATA_LANGUAGE = "language";

    private long id;
    private String guid;
    private String hruid;
    private String legacyAltPid;
    private String legacyShortId;
    private boolean isLocked;
    private Long createdByClientId;
    private Long auth0TenantId;
    private String auth0UserId;
    private long createdAt;
    private long updatedAt;
    private Long expiresAt;
    private String email;

    private UserProfile profile;
    private MailAddress address;

    @JdbiConstructor
    public User(@ColumnName("user_id") long id,
                @ColumnName("user_guid") String guid,
                @ColumnName("user_hruid") String hruid,
                @ColumnName("legacy_altpid") String legacyAltPid,
                @ColumnName("legacy_shortid") String legacyShortId,
                @ColumnName("is_locked") boolean isLocked,
                @ColumnName("created_by_client_id") Long createdByClientId,
                @ColumnName("auth0_tenant_id") Long auth0TenantId,
                @ColumnName("auth0_user_id") String auth0UserId,
                @ColumnName("created_at") long createdAt,
                @ColumnName("updated_at") long updatedAt,
                @ColumnName("expires_at") Long expiresAt,
                @ColumnName("email") String email) {
        this.id = id;
        this.guid = guid;
        this.hruid = hruid;
        this.legacyAltPid = legacyAltPid;
        this.legacyShortId = legacyShortId;
        this.isLocked = isLocked;
        this.createdByClientId = createdByClientId;
        this.auth0TenantId = auth0TenantId;
        this.auth0UserId = auth0UserId;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.expiresAt = expiresAt;
        this.email = email;
    }

    public Optional<Long> getAuth0TenantId() {
        return Optional.ofNullable(auth0TenantId);
    }

    /**
     * Returns the user's Auth0 id, if the user has one.
     * 
     * <p>If {@link User#hasAuth0Account()} returns true, the user is guaranteed
     * to have an Auth0 id present.
     */
    public Optional<String> getAuth0UserId() {
        return Optional.ofNullable(auth0UserId);
    }

    /**
     * Returns the email associated with the user.
     * @return an email address, if the user has one
     */
    public Optional<String> getEmail() {
        return Optional.ofNullable(email);
    }

    public boolean hasProfile() {
        return profile != null;
    }

    public boolean hasAddress() {
        return address != null;
    }

    public boolean isTemporary() {
        return expiresAt != null;
    }

    public boolean isExpired() {
        return expiresAt != null && expiresAt <= Instant.now().toEpochMilli();
    }

    public boolean hasAuth0Account() {
        return StringUtils.isNotEmpty(auth0UserId);
    }

    /**
     * An account is considered to be internal if it has an assigned email address,
     * but does not have an associated Auth0 account.
     * 
     * @return true if the account is internal, false otherwise.
     */
    public boolean isInternalAccount() {
        return (hasAuth0Account() == false) && (StringUtils.isNotEmpty(email));
    }
}

package org.broadinstitute.ddp.model.user;

import java.time.Instant;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Representation of a user in the system, with associations to it's profile and address which are considered
 * system-wide resources.
 */
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
    private long createdByClientId;
    private long auth0TenantId;
    private String auth0UserId;
    private long createdAt;
    private long updatedAt;
    private Long expiresAt;

    // Auth0 account email. Often not needed and expensive to compute since it requires an API call to Auth0,
    // so fill this manually when necessary.
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
                @ColumnName("created_by_client_id") long createdByClientId,
                @ColumnName("auth0_tenant_id") long auth0TenantId,
                @ColumnName("auth0_user_id") String auth0UserId,
                @ColumnName("created_at") long createdAt,
                @ColumnName("updated_at") long updatedAt,
                @ColumnName("expires_at") Long expiresAt) {
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
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getGuid() {
        return guid;
    }

    public String getHruid() {
        return hruid;
    }

    public String getLegacyAltPid() {
        return legacyAltPid;
    }

    public String getLegacyShortId() {
        return legacyShortId;
    }

    public boolean isLocked() {
        return isLocked;
    }

    public long getCreatedByClientId() {
        return createdByClientId;
    }

    public long getAuth0TenantId() {
        return auth0TenantId;
    }

    public String getAuth0UserId() {
        return auth0UserId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserProfile getProfile() {
        return profile;
    }

    public void setProfile(UserProfile profile) {
        this.profile = profile;
    }

    public boolean hasProfile() {
        return profile != null;
    }

    public MailAddress getAddress() {
        return address;
    }

    public void setAddress(MailAddress address) {
        this.address = address;
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
}

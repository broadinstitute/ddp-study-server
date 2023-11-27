package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface UserSql extends SqlObject {

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertByClientIdOrAuth0Ids")
    long insertByClientIdOrAuth0Ids(
            @Define("byClientId") boolean byClientId,
            @Bind("clientId") Long clientId,
            @Bind("auth0Domain") String auth0Domain,
            @Bind("auth0ClientId") String auth0ClientId,
            @Bind("auth0UserId") String auth0UserId,
            @Bind("guid") String guid,
            @Bind("hruid") String hruid,
            @Bind("legacyAltPid") String legacyAltPid,
            @Bind("legacyShortId") String legacyShortId,
            @Bind("isLocked") boolean isLocked,
            @Bind("createdAt") long createdAt,
            @Bind("updatedAt") long updatedAt,
            @Bind("expiresAt") Long expiresAt);
    
    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertUserByEmail")
    long insertByEmail(@Bind("guid") String guid,
                    @Bind("email") String email,
                    @Bind("hruid") String hruid,
                    @Bind("isLocked") boolean isLocked,
                    @Bind("createdAt") long createdAt,
                    @Bind("updatedAt") long updatedAt);

    @SqlUpdate("update user set auth0_user_id = :auth0UserId, expires_at = null where user_id = :id")
    int updateAuth0UserIdAndClearExpiresAtById(@Bind("id") long userId, @Bind("auth0UserId") String auth0UserId);

    @SqlUpdate("update user set legacy_altpid = :altpid where user_id = :id")
    int updateLegacyAltPidById(@Bind("id") long userId, @Bind("altpid") String legacyAltPid);

    @SqlUpdate("UPDATE user SET "
            + "created_by_client_id = :createdByClientId, "
            + "auth0_tenant_id = :auth0TenantId, "
            + "auth0_user_id = :auth0UserId, "
            + "is_locked = :isLocked, "
            + "updated_at = :updatedAt, "
            + "expires_at = :expiresAt "
            + "WHERE user_id = :userId")
    int updateUser(@Bind("userId") long userId,
            @Bind("createdByClientId") Long createdByClientId,
            @Bind("auth0TenantId") Long auth0TenantId,
            @Bind("auth0UserId") String auth0UserId,
            @Bind("isLocked") boolean isLocked,
            @Bind("updatedAt") long updatedAt,
            @Bind("expiresAt") Long expiresAt);
}

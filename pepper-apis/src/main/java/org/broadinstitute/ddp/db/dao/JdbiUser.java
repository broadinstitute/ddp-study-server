package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.broadinstitute.ddp.db.dto.UserDto;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface JdbiUser extends SqlObject {
    @SqlQuery("select guid from user where user_id = :user_id")
    String getUserGuidById(@Bind("user_id") long userId);

    @SqlQuery("select user_id from user where auth0_user_id = :auth0UserId and auth0_tenant_id = :auth0TenantId")
    long getUserIdByAuth0UserId(@Bind("auth0UserId") String auth0UserId, @Bind("auth0TenantId") long auth0TenantId);

    @SqlQuery("select user_id from user where guid = :userGuid")
    long getUserIdByGuid(@Bind("userGuid") String userGuid);

    @SqlQuery("select guid from user where legacy_altpid = :altpid")
    String getUserGuidByAltpid(@Bind("altpid") String altpid);

    @SqlQuery("select user_id from user where hruid is NULL")
    List<Long> getUserIdsForUsersWithoutHruids();

    @SqlQuery("select count(*) from user")
    long getTotalUserCount();

    @SqlUpdate("update user set hruid = :hruid where user_id = :userId")
    int updateUserHruid(@Bind("userId") long userId, @Bind("hruid") String hruid);

    @SqlUpdate("insert into user (auth0_user_id,guid,created_by_client_id,is_locked, hruid, auth0_tenant_id, created_at, updated_at) "
            + "(select :auth0UserId,:userGuid,c.client_id,false,:userHruid,t.auth0_tenant_id,:createdAt,:updatedAt "
            + "from auth0_tenant t, client c where t.auth0_tenant_id = c.auth0_tenant_id and c.client_id = :clientId)")
    @GetGeneratedKeys
    long insert(@Bind("auth0UserId") String auth0UserId, @Bind("userGuid") String userGuid,
                @Bind("clientId") long clientId, @Bind("userHruid") String userHruid,
                @Bind("createdAt") long createdAt, @Bind("updatedAt") long updatedAt);

    default long insert(String auth0UserId, String userGuid, long clientId, String userHruid) {
        long now = Instant.now().toEpochMilli();
        return insert(auth0UserId, userGuid, clientId, userHruid, now, now);
    }

    @SqlUpdate("insert into user (auth0_user_id,guid,created_by_client_id,is_locked, hruid, "
            + "legacy_altpid, legacy_shortid, created_at, updated_at, auth0_tenant_id) "
            + "(select :auth0UserId,:userGuid,:clientId,false,:userHruid,:legacyAltpid,:legacyShortid,:createdAt,"
            + " :updatedAt, t.auth0_tenant_id "
            + " from auth0_tenant t, client c where t.auth0_tenant_id = c.auth0_tenant_id and c.client_id = :clientId)")
    @GetGeneratedKeys
    long insertMigrationUser(@Bind("auth0UserId") String auth0UserId, @Bind("userGuid") String userGuid,
                             @Bind("clientId") long clientId, @Bind("userHruid") String userHruid,
                             @Bind("legacyAltpid") String legacyAltpid, @Bind("legacyShortid") String legacyShortid,
                             @Bind("createdAt") long createdAt, @Bind("updatedAt") long updatedAt);

    @SqlQuery("select * from user where guid = :guid")
    @RegisterConstructorMapper(UserDto.class)
    UserDto findByUserGuid(@Bind("guid") String userGuid);

    @SqlQuery("select * from user where legacy_altpid = :legacyAltPid")
    @RegisterConstructorMapper(UserDto.class)
    UserDto findByLegacyAltPid(@Bind("legacyAltPid") String legacyAltPid);

    @SqlQuery("select * from user where user_id in (<userIds>)")
    @RegisterConstructorMapper(UserDto.class)
    List<UserDto> findByUserIds(@BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) List<Long> userIds);

    default UserDto findByUserId(long userId) {
        List<UserDto> userList = findByUserIds(Collections.singletonList(userId));
        return userList.isEmpty() ? null : userList.get(0);
    }

    @SqlQuery("select * from user where auth0_user_id = :auth0UserId and auth0_tenant_id = :auth0TenantId")
    @RegisterConstructorMapper(UserDto.class)
    UserDto findByAuth0UserId(@Bind("auth0UserId") String auth0UserId, @Bind("auth0TenantId") long auth0TenantId);

    /**
     * Sets the auth0UserId for the given user guid.  Should only be used during tests
     * because auth0UserId should be set during creation of a user via {@link org.broadinstitute.ddp.route.UserRegistrationRoute}
     *
     * @param userGuid    the user guid to update
     * @param auth0UserId the value of auth0_user_id
     * @return number of rows updated
     */
    @SqlUpdate("update user set auth0_user_id = :auth0UserId where guid = :userGuid")
    int updateAuth0Id(@Bind("userGuid") String userGuid, @Bind("auth0UserId") String auth0UserId);

    @SqlUpdate("update user set expires_at = :expiresAt where user_id = :userId")
    int updateExpiresAtById(@Bind("userId") long userId, @Bind("expiresAt") Long expiresAt);

    default UserDto findByLegacyAltPidIfNotFoundByUserGuid(String userGuidOrLegacyAltPid) {
        UserDto userDto = findByUserGuid(userGuidOrLegacyAltPid);
        if (userDto == null) {
            userDto = findByLegacyAltPid(userGuidOrLegacyAltPid);
        }
        return userDto;
    }

    @SqlUpdate("delete from user where user_id in (<ids>)")
    int deleteAllByIds(@BindList(value = "ids", onEmpty = BindList.EmptyHandling.NULL) Set<Long> ids);

    @SqlUpdate("delete from user where guid in (<guid>)")
    int deleteAllByGuids(@BindList(value = "guid", onEmpty = BindList.EmptyHandling.NULL) Set<String> guids);
}

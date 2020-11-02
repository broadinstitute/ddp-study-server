package org.broadinstitute.ddp.db.dao;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface UserDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(UserDao.class);

    /**
     * Amount of time a temporary user is valid for until it expires.
     */
    long EXPIRATION_DURATION_MILLIS = TimeUnit.HOURS.toMillis(24);

    @CreateSqlObject
    ActivityInstanceDao getActivityInstanceDao();

    @CreateSqlObject
    JdbiUser getJdbiUser();

    @CreateSqlObject
    UserSql getUserSql();


    default User createUser(String auth0Domain, String auth0ClientId, String auth0UserId) {
        return createUserByClientIdOrAuth0Ids(false, null, auth0Domain, auth0ClientId, auth0UserId, false, null);
    }

    default User createUser(long clientId, String auth0UserId, String legacyAltPid) {
        return createUserByClientIdOrAuth0Ids(true, clientId, null, null, auth0UserId, false, legacyAltPid);
    }

    default User createTempUser(String auth0Domain, String auth0ClientId) {
        return createUserByClientIdOrAuth0Ids(false, null, auth0Domain, auth0ClientId, null, true, null);
    }

    default User createTempUser(long clientId) {
        return createUserByClientIdOrAuth0Ids(true, clientId, null, null, null, true, null);
    }

    private User createUserByClientIdOrAuth0Ids(boolean byClientId, Long clientId,
                                                String auth0Domain, String auth0ClientId,
                                                String auth0UserId, boolean isTemporary, String legacyAltPid) {
        Handle handle = getHandle();
        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);

        long now = Instant.now().toEpochMilli();
        Long expiresAt = isTemporary ? now + EXPIRATION_DURATION_MILLIS : null;

        long userId = getUserSql().insertByClientIdOrAuth0Ids(
                byClientId, clientId, auth0Domain, auth0ClientId, auth0UserId,
                userGuid, userHruid, legacyAltPid, null, false, now, now, expiresAt);
        return findUserById(userId).orElseThrow(() -> new DaoException("Could not find user with id " + userId));
    }


    @UseStringTemplateSqlLocator
    @SqlQuery("queryUserById")
    @RegisterConstructorMapper(User.class)
    Optional<User> findUserById(@Bind("id") long userId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryUserByGuid")
    @RegisterConstructorMapper(User.class)
    Optional<User> findUserByGuid(@Bind("guid") String userGuid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryUserByHruid")
    @RegisterConstructorMapper(User.class)
    Optional<User> findUserByHruid(@Bind("hruid") String userHruid);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryUserByAuth0UserId")
    @RegisterConstructorMapper(User.class)
    Optional<User> findUserByAuth0UserId(@Bind("auth0UserId") String auth0UserId, @Bind("auth0TenantId") long auth0TenantId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryUserByGuidOrAltPid")
    @RegisterConstructorMapper(User.class)
    Optional<User> findUserByGuidOrAltPid(@Bind("guidOrAltPid") String userGuidOrAltPid);

    @SqlQuery("select user_id from user where expires_at is not null and expires_at <= :now")
    Set<Long> findExpiredTemporaryUserIds(@Bind("now") long nowMillis);

    default void upgradeUserToPermanentById(long userId, String auth0UserId) {
        DBUtils.checkUpdate(1, getUserSql().updateAuth0UserIdAndClearExpiresAtById(userId, auth0UserId));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryUsersAndProfilesByGuids")
    @RegisterConstructorMapper(value = User.class, prefix = "u")
    @RegisterConstructorMapper(value = UserProfile.class, prefix = "p")
    @UseRowReducer(UserWithProfileReducer.class)
    Stream<User> findUsersAndProfilesByGuids(
            @BindList(value = "userGuids", onEmpty = BindList.EmptyHandling.NULL) Set<String> userGuids);

    @UseStringTemplateSqlLocator
    @SqlUpdate("deleteAllTempUserRelatedDataByUserIds")
    int _deleteAllTempUserRelatedDataByUserIds(@BindList(value = "userIds", onEmpty = BindList.EmptyHandling.NULL) Set<Long> userIds);

    default int deleteAllExpiredTemporaryUsers() {
        ActivityInstanceDao instanceDao = getActivityInstanceDao();
        JdbiUser jdbiUser = getJdbiUser();

        long now = Instant.now().toEpochMilli();
        LOG.info("Using timestamp {} ms to find expired temporary users", now);

        Set<Long> expiredTempUserIds = findExpiredTemporaryUserIds(now);
        LOG.info("Found {} expired temporary users to delete", expiredTempUserIds.size());

        if (expiredTempUserIds.isEmpty()) {
            return 0;
        }

        Set<Long> instanceIds = instanceDao.findAllInstanceIdsByUserIds(expiredTempUserIds);
        LOG.info("Found {} activity instances to delete", instanceIds.size());
        int numDeleted = instanceDao.deleteAllByIds(instanceIds);
        LOG.info("Deleted {} activity instances", numDeleted);
        if (numDeleted != instanceIds.size()) {
            throw new DaoException("Could not delete all activity instances for expired temporary users");
        }

        numDeleted = getHandle().attach(DataExportDao.class).deleteDataSyncRequestsForUsers(expiredTempUserIds);
        LOG.info("Deleted {} data sync requests", numDeleted);

        numDeleted = getHandle().attach(QueuedEventDao.class).deleteQueuedEventsByUserIds(expiredTempUserIds);
        LOG.info("Deleted {} queued events", numDeleted);

        numDeleted = _deleteAllTempUserRelatedDataByUserIds(expiredTempUserIds);
        LOG.info("Deleted {} rows of various user data (profile, event_counter)", numDeleted);

        numDeleted = jdbiUser.deleteAllByIds(expiredTempUserIds);
        LOG.info("Deleted {} expired temporary users", numDeleted);
        if (numDeleted != expiredTempUserIds.size()) {
            throw new DaoException("Could not delete all expired temporary users");
        }

        return numDeleted;
    }

    default void assignAuth0UserId(String userGuid, String auth0UserId) {
        DBUtils.checkUpdate(1, updateAuth0UserId(userGuid, auth0UserId));
    }

    @SqlUpdate("update user set auth0_user_id  = :auth0UserId where guid = :guid")
    int updateAuth0UserId(@Bind("guid") String guid, @Bind("auth0UserId") String auth0UserId);

    class UserWithProfileReducer implements LinkedHashMapRowReducer<Long, User> {
        @Override
        public void accumulate(Map<Long, User> container, RowView row) {
            long userId = row.getColumn("u_user_id", Long.class);
            if (!container.containsKey(userId)) {
                User user = row.getRow(User.class);
                if (row.getColumn("p_user_id", Long.class) != null) {
                    user.setProfile(row.getRow(UserProfile.class));
                }
                container.put(userId, user);
            }
        }
    }
}

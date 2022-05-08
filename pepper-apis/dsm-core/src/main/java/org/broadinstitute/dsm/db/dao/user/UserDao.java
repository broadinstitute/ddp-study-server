package org.broadinstitute.dsm.db.dao.user;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.DdpDBUtils;
import org.broadinstitute.dsm.db.DDPInstance;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.dto.user.UserRoleDto;
import org.broadinstitute.dsm.db.jdbi.JdbiUser;
import org.broadinstitute.dsm.db.jdbi.JdbiUserRole;
import org.broadinstitute.dsm.db.jdbi.JdbiUserSettings;
import org.broadinstitute.dsm.exception.DaoException;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserDao implements Dao<UserDto> {
    Logger logger = LoggerFactory.getLogger(UserDao.class);

    public Optional<UserDto> getUserByEmail(@NonNull String email) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).getUserByEmail(email);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by email ", results.resultException);
        }

        return Optional.ofNullable((UserDto) results.resultValue);
    }

    @Override
    public Optional<UserDto> get(long userId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).getUserByUserId(userId);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by id ", results.resultException);
        }
        return Optional.ofNullable((UserDto) results.resultValue);
    }

    @Override
    public int create(UserDto userDto) {
        return -1;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    public int delete(Long id) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).deleteByUserId(id);
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Error deleting user with "
                    + id, results.resultException);
        }
        return (int) results.resultValue;
    }

    public Map<Integer, String> getAllUserMap() {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            List<UserDto> res = handle.attach(JdbiUser.class).getUserMap();
            Map<Long, String> map = new HashMap<>();
            if (res != null) {
                for (UserDto user : res) {
                    user.getName().ifPresentOrElse(name -> map.put(user.getUserId(), name),
                            () -> map.put(user.getUserId(), user.getEmail().orElseThrow()));
                }
            }
            dbVals.resultValue = map;
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting all users  ", results.resultException);
        }

        return (Map<Integer, String>) results.resultValue;
    }

    public List<String> getAllUserPermissions(long userId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).getAllUserPermissions(userId);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by id ", results.resultException);
        }
        return (List<String>) results.resultValue;
    }

    public List<UserDto> getAllDSMUsers() {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).getAllDSMUsers();
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by id ", results.resultException);
        }
        return (List<UserDto>) results.resultValue;
    }

    public String getUserGuid(String email) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).findUserGUID(email);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user by id ", results.resultException);
        }
        return (String) results.resultValue;
    }

    public long insertNewUser(String auth0Domain, String clientKey, UserRoleDto userRoleDto, DDPInstance ddpInstance) {
        UserDto user = userRoleDto.getUser();
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            Optional<Long> maybeUserId = handle.attach(JdbiUser.class).selectUserIdByEMail(user.getEmail().orElseThrow());
            long userId;
            if (!maybeUserId.isEmpty()) {
                userId = maybeUserId.get();
                logger.info("user " + user.getEmail() + " already exists, user id: " + userId);
            } else {
                String userGuid = DdpDBUtils.uniqueUserGuid(handle);
                String userHruid = DdpDBUtils.uniqueUserHruid(handle);
                long now = Instant.now().toEpochMilli();
                Long expiresAt = null;

                userId = handle.attach(JdbiUser.class).insertUser(auth0Domain, clientKey, null,
                        userGuid, userHruid, null, null, false, now, now, expiresAt, true);
                String phone = user.getPhoneNumber().orElse("");
                handle.attach(JdbiUser.class)
                        .insertUserProfile(Long.valueOf(userId), user.getFirstName(), user.getLastName(), phone,
                                user.getEmail().orElseThrow());
                handle.attach(JdbiUserSettings.class).insertNewUserSettings(userId);
                logger.info("Inserted " + user.getEmail().get() + " as userId " + userId + " into DSS database");
            }
            try {
                DBUtil.checkUpdate(
                        handle.attach(JdbiUserRole.class)
                                .insertNewUserRole(userId, userRoleDto.getRole().getRoleName(), ddpInstance.getStudyGuid()),
                        1);
            } catch (DaoException e) {
                dbVals.resultException = e;
            }

            dbVals.resultValue = userId;
            return dbVals;
        });
        if (results.resultException != null) {
            throw new RuntimeException("Problem inserting new user " + user.getEmail().get(), results.resultException);
        }

        return (long) results.resultValue;
    }

    public void updateAuth0UserId(long userId, String auth0UserId) throws DaoException {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            DBUtil.checkUpdate(1, handle.attach(JdbiUser.class).updateAuth0UserId(userId, auth0UserId));
            return dbVals;
        });
    }

    public void deactivateUser(long userId) {
        TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            try {
                DBUtil.checkUpdate(1, handle.attach(JdbiUser.class).deactivateUser(userId));
            } catch (DaoException e) {
                throw new RuntimeException("Problem deactivating user with id " + userId, e);
            }
            return null;
        });
    }
}

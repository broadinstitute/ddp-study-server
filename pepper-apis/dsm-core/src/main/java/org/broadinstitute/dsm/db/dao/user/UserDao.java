package org.broadinstitute.dsm.db.dao.user;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.util.DdpDBUtils;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.dsm.db.jdbi.JdbiUser;
import org.broadinstitute.lddp.db.SimpleResult;

public class UserDao implements Dao<UserDto> {

    long EXPIRATION_DURATION_MILLIS = TimeUnit.HOURS.toMillis(24);

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

    public long insertNewUser(Long clientId,
                              String auth0Domain, String auth0ClientId,
                              String auth0UserId, boolean isTemporary) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            String userGuid = DdpDBUtils.uniqueUserGuid(handle);
            String userHruid = DdpDBUtils.uniqueUserHruid(handle);

            long now = Instant.now().toEpochMilli();
            Long expiresAt = isTemporary ? now + EXPIRATION_DURATION_MILLIS : null;

            long userId = handle.attach(JdbiUser.class).insertUser(
                    auth0Domain, auth0ClientId, auth0UserId,
                    userGuid, userHruid, null, null,
                    false, now, now, expiresAt);
            dbVals.resultValue = userId;

            return dbVals;
        });

        return (long) results.resultValue;
    }

    public void updateAuth0UserId(long userId, String auth0UserId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            handle.attach(JdbiUser.class).updateAuth0UserId(userId, auth0UserId);
            return dbVals;
        });
    }
}

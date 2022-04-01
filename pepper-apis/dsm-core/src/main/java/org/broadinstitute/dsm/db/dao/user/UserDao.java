package org.broadinstitute.dsm.db.dao.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.lddp.db.SimpleResult;

public class UserDao implements Dao<UserDto> {

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
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
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
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUser.class).insert(userDto.getName().orElse(""), userDto.getEmail().orElse(""));
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error inserting user by email " + userDto.getEmail(), results.resultException);
        }
        return (int) results.resultValue;
    }

    @Override
    public int delete(int id) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
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
            Map<Integer, String> map = new HashMap<>();
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
}

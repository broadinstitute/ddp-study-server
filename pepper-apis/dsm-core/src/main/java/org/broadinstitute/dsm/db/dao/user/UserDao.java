package org.broadinstitute.dsm.db.dao.user;

import java.util.Optional;

import lombok.NonNull;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.user.UserDto;
import org.broadinstitute.lddp.db.SimpleResult;

public class UserDao implements Dao<UserDto> {

    public Optional<UserDto> getUserByEmail(@NonNull String email) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.DSM, handle -> {
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
}

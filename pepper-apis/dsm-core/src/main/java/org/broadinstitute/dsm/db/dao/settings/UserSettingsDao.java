package org.broadinstitute.dsm.db.dao.settings;


import java.util.Optional;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.dsm.db.dao.Dao;
import org.broadinstitute.dsm.db.dto.settings.UserSettingsDto;
import org.broadinstitute.dsm.db.jdbi.JdbiUserSettings;
import org.broadinstitute.dsm.exception.DaoException;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.lddp.db.SimpleResult;

public class UserSettingsDao implements Dao<UserSettingsDto> {


    public long create(long userId) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserSettings.class).insertNewUserSettings(userId);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error inserting new user settings for user Id" + userId, results.resultException);
        }
        return (long) results.resultValue;
    }

    public void updateUserSettings(long userId, UserSettingsDto userSettingsDto) throws DaoException {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            int numRows = handle.attach(JdbiUserSettings.class).updateUserSettings(userSettingsDto.getRowsPerPage(), userId);
            DBUtil.checkUpdate(1, numRows);
            return dbVals;
        });

    }

    @Override
    public int create(UserSettingsDto userSettingsDto) {
        return 0;
    }

    @Override
    public int delete(int id) {
        return 0;
    }

    @Override
    public Optional<UserSettingsDto> get(long id) {
        SimpleResult results = TransactionWrapper.withTxn(TransactionWrapper.DB.SHARED_DB, handle -> {
            SimpleResult dbVals = new SimpleResult();
            dbVals.resultValue = handle.attach(JdbiUserSettings.class).getUserSettingsFromUserId(id);
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting user settings for user Id" + id, results.resultException);
        }
        return Optional.ofNullable((UserSettingsDto) results.resultValue);
    }
}

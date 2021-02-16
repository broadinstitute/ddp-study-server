package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_FIRST_NAME;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_LAST_NAME;

import java.util.Properties;


import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.jdbi.v3.core.Handle;

public class UpdateFirstLastNameHandler {

    public void updateFirstLastName(String userGuid, Properties payload) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateFirstLastName(handle, userGuid, payload));
    }

    private void updateFirstLastName(Handle handle, String userGuid, Properties payload) {
        var profileDao = handle.attach(UserProfileDao.class);
        var profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throwUserNotFoundException(userGuid);
        }
        String firstName = detectFieldValueForUpdate(payload, FIELD_FIRST_NAME, profile.getFirstName());
        String lastName = detectFieldValueForUpdate(payload, FIELD_LAST_NAME, profile.getLastName());
        int count = profileDao.getUserProfileSql().updateFirstAndLastNameByUserGuid(userGuid, firstName, lastName);
        if (count > 0) {
            syncToElastic(handle, userGuid);
        } else {
            throwUserNotFoundException(userGuid);
        }
    }

    private String detectFieldValueForUpdate(Properties payload, String fieldName, String currentValue) {
        return payload.containsKey(fieldName) ? payload.getProperty(fieldName) : currentValue;
    }

    private void throwUserNotFoundException(String userGuid) {
        throw new PubSubTaskException("User profile is not found for guid=" + userGuid);
    }

    private void syncToElastic(Handle handle, String userGuid) {
        handle.attach(DataExportDao.class).queueDataSync(userGuid);
    }
}

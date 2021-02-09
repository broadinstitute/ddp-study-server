package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import java.util.Map;


import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.jdbi.v3.core.Handle;

public class UpdateFirstLastNameHandler {

    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";

    public void updateFirstLastName(String userGuid, Map<String, String> payload) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateFirstLastName(handle, userGuid, payload));
    }

    private void updateFirstLastName(Handle handle, String userGuid, Map<String, String> payload) {
        var profileDao = handle.attach(UserProfileDao.class);
        var profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throw new PubSubTaskException("User profile is not found for guid=" + userGuid);
        }
        String firstName = detectFieldValueForUpdate(payload, FIELD_FIRST_NAME, profile.getFirstName());
        String lastName = detectFieldValueForUpdate(payload, FIELD_LAST_NAME, profile.getLastName());
        int count = profileDao.getUserProfileSql().updateFirstAndLastNameByUserGuid(userGuid, firstName, lastName);
        if (count == 0) {
            throw new PubSubTaskException("User profile is not found for guid=" + userGuid);
        }
    }

    private String detectFieldValueForUpdate(Map<String, String> payload, String fieldName, String currentValue) {
        return payload.containsKey(fieldName) ? payload.get(fieldName) : currentValue;
    }
}

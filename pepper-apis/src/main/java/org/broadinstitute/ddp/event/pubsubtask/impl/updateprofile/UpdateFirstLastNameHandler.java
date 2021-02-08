package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTask;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.jdbi.v3.core.Handle;

public class UpdateFirstLastNameHandler {

    private static final String FIELD_FIRST_NAME = "firstName";
    private static final String FIELD_LAST_NAME = "lastName";

    public void updateFirstLastName(String userGuid, PubSubTask.PayloadMap payloadMap) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateFirstLastName(handle, userGuid, payloadMap));
    }

    private void updateFirstLastName(Handle handle, String userGuid, PubSubTask.PayloadMap payloadMap) {
        var profileDao = handle.attach(UserProfileDao.class);
        var profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throw new PubSubTaskException("User profile is not found for guid=" + userGuid);
        }
        String firstName = detectFieldValueForUpdate(payloadMap, FIELD_FIRST_NAME, profile.getFirstName());
        String lastName = detectFieldValueForUpdate(payloadMap, FIELD_LAST_NAME, profile.getLastName());
        int count = profileDao.getUserProfileSql().updateFirstAndLastNameByUserGuid(userGuid, firstName, lastName);
        if (count == 0) {
            throw new PubSubTaskException("User profile is not found for guid=" + userGuid);
        }
    }

    private String detectFieldValueForUpdate(PubSubTask.PayloadMap payloadMap, String fieldName, String currentValue) {
        return payloadMap.getMap().containsKey(fieldName) ? payloadMap.getMap().get(fieldName) : currentValue;
    }
}

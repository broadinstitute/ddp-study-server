package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.dsmtask.api.DsmTaskException;
import org.jdbi.v3.core.Handle;

public class UpdateFirstLastNameHandler {

    public void updateFirstLastName(String userGuid, String firstName, String lastName) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateFirstLastName(handle, userGuid, firstName, lastName));
    }

    private void updateFirstLastName(Handle handle, String userGuid, String firstName, String lastName) {
        var profileDao = handle.attach(UserProfileDao.class);
        var profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throw new DsmTaskException("User profile is not found for guid=" + userGuid);
        }
        if (firstName == null) {
            firstName = profile.getFirstName();
        }
        if (lastName == null) {
            lastName = profile.getLastName();
        }
        int count = profileDao.getUserProfileSql().updateFirstAndLastNameByUserGuid(userGuid, firstName, lastName);
        if (count == 0) {
            throw new DsmTaskException("User profile is not found for guid=" + userGuid);
        }
    }
}

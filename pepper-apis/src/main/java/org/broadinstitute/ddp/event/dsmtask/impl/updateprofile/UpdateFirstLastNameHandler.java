package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;

public class UpdateFirstLastNameHandler {

    public void doIt(String userGuid, String firstName, String lastName) {
        TransactionWrapper.useTxn(handle -> {
            updateFirstLastName(handle, userGuid, firstName, lastName);
        });
    }

    private void updateFirstLastName(Handle handle, String userGuid, String firstName, String lastName) {
        var profileDao = handle.attach(UserProfileDao.class);
        UserProfile profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throw new DDPException("Profile not found for user with guid: " + userGuid);
        }
        var builder = new UserProfile.Builder(profile);
        if (firstName != null) {
            builder.setFirstName(firstName);
        }
        if (lastName != null) {
            builder.setLastName(lastName);
        }
        profileDao.updateProfile(builder.build());
    }
}

package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskConstants.LOG_PREFIX_DSM_TASK;
import static org.slf4j.LoggerFactory.getLogger;


import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;

public class UpdateFirstLastNameHandler {

    private static final Logger LOG = getLogger(UpdateFirstLastNameHandler.class);

    public void doIt(String userGuid, String firstName, String lastName) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateFirstLastName(handle, userGuid, firstName, lastName));
    }

    private void updateFirstLastName(Handle handle, String userGuid, String firstName, String lastName) {
        var profileDao = handle.attach(UserProfileDao.class);
        UserProfile profile = profileDao.findProfileByUserGuid(userGuid).orElse(null);
        if (profile == null) {
            throw new DDPException("Profile not found for user with guid: " + userGuid);
        }
        if (StringUtils.compare(profile.getFirstName(), firstName) == 0
                && StringUtils.compare(profile.getLastName(), lastName) == 0) {
            LOG.info(LOG_PREFIX_DSM_TASK, "did not change firstName={} or lastName={}, because it is equal to current",
                    firstName, lastName);
        } else {
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
}

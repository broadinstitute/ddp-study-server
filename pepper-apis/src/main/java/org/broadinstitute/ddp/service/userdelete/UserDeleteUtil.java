package org.broadinstitute.ddp.service.userdelete;

import static java.lang.String.format;

import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.user.User;
import org.jdbi.v3.core.Handle;

public class UserDeleteUtil {

    public static String errMsg(String message, Object... params) {
        return format("Error during user full deletion: " + message, params);
    }

    public static User getUser(Handle handle, String userGuid) {
        UserDao userDao = handle.attach(UserDao.class);
        return userDao.findUserByGuid(userGuid)
                .orElseThrow(() -> new DDPException(errMsg("user not found with GUID=%s", userGuid)));
    }

    /**
     * Check if user has governed users.
     */
    public static boolean hasGovernedUsers(Handle handle, String userGuid) {
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        return userGovernanceDao.findActiveGovernancesByProxyGuid(userGuid).count() > 0;
    }
}

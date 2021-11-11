package org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.ERROR;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException.Severity.WARN;
import static org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskLogUtil.infoMsg;
import static org.broadinstitute.ddp.event.pubsubtask.impl.updateprofile.UpdateProfileConstants.FIELD_EMAIL;
import static org.slf4j.LoggerFactory.getLogger;

import java.util.Properties;


import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.event.pubsubtask.api.PubSubTaskException;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;

public class UpdateEmailHandler {

    private static final Logger LOG = getLogger(UpdateEmailHandler.class);

    public void updateEmail(String userGuid, Properties payload) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateEmail(handle, userGuid, payload));
    }

    private void updateEmail(Handle handle, String userGuid, Properties payload) {
        if (payload.containsKey(FIELD_EMAIL)) {
            String email = payload.getProperty(FIELD_EMAIL);
            var userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
            if (userDto == null) {
                throw new PubSubTaskException("User profile is not found for guid=" + userGuid, WARN);
            }
            validateUserForLoginDataUpdateEligibility(userDto);

            LOG.info(infoMsg("Attempting to change the email of the user {}"), userGuid);

            updateEmailInAuth0(handle, userDto, email, userGuid);
        }
    }

    private void validateUserForLoginDataUpdateEligibility(UserDto userDto) {
        String errMsg = null;
        if (userDto == null) {
            errMsg = "User " + userDto.getUserGuid() + " does not exist in Pepper";
        }
        if (userDto.getAuth0UserId() == null) {
            errMsg = "User " + userDto.getUserGuid() + " is not associated with the Auth0 user " + userDto.getAuth0UserId();
        }
        if (errMsg != null) {
            throw new PubSubTaskException(errMsg, WARN);
        }
    }

    /**
     * NOTE: Similar logic is implemented in class UpdateUserEmailRoute.java
     */
    private void updateEmailInAuth0(Handle handle, UserDto userDto, String email, String userGuid) {
        var mgmtAPI = Auth0Util.getManagementApiInstanceForUser(userDto.getUserGuid(), handle);
        // Note: when updating email administratively through DSM, we assume email has been verified.
        var status = Auth0Util.updateUserEmail(mgmtAPI, userDto, email, true);
        String errMsg = null;
        switch (status.getAuth0Status()) {
            case SUCCESS:
                syncToElastic(handle, userDto.getUserId());
                LOG.info(infoMsg("The email of the user {} was successfully changed"), userGuid);
                break;
            case INVALID_TOKEN:
                errMsg = "The provided Auth0 token is invalid";
                break;
            case MALFORMED_EMAIL:
                errMsg = "The new email " + email + " is malformed and was rejected by Auth0";
                break;
            case EMAIL_ALREADY_EXISTS:
                errMsg = "The new email " + email + " already exists in Auth0, please choose another";
                break;
            case UNKNOWN_PROBLEM:
                errMsg = "An unknown problem happened in Pepper or Auth0";
                if (status.getErrorMessage() != null) {
                    errMsg = errMsg + " Auth0 message: " + status.getErrorMessage();
                }
                throw new PubSubTaskException(errMsg, ERROR, true);
            default:
                errMsg = "The returned Auth0 call status is unknown - something completely unexpected happened";
        }
        if (errMsg != null) {
            throw new PubSubTaskException(errMsg, WARN);
        }
    }

    private void syncToElastic(Handle handle, long userId) {
        handle.attach(DataExportDao.class).queueDataSync(userId, true);
    }
}

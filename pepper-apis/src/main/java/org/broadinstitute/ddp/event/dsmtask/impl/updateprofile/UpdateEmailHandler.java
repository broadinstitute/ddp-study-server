package org.broadinstitute.ddp.event.dsmtask.impl.updateprofile;

import static org.broadinstitute.ddp.event.dsmtask.api.DsmTaskLogUtil.infoMsg;
import static org.slf4j.LoggerFactory.getLogger;


import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.TransactionWrapper.DB;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.Auth0Util;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;

public class UpdateEmailHandler {

    private static final Logger LOG = getLogger(UpdateEmailHandler.class);

    public void doIt(String userGuid, String email) {
        TransactionWrapper.useTxn(DB.APIS, handle -> updateEmail(handle, userGuid, email));
    }

    private void updateEmail(Handle handle, String userGuid, String email) {
        UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
        validateUserForLoginDataUpdateEligibility(userDto);
        LOG.info(infoMsg("Attempting to change the email of the user {}"), userGuid);
        ManagementAPI mgmtAPI = Auth0Util.getManagementApiInstanceForUser(userDto.getUserGuid(), handle);

        User auth0User = getUserDataFromAuth0(mgmtAPI, userDto.getAuth0UserId());
        if (!auth0User.getEmail().equals(email)) {
            updateEmailInAuth0(handle, mgmtAPI, userDto, email, userGuid);
        } else {
            LOG.info(infoMsg("Email {} is not updated, because it is equal to current"), email);
        }
    }

    private void validateUserForLoginDataUpdateEligibility(UserDto userDto) {
        String errMsg = null;
        if (userDto == null) {
            errMsg = "User " + userDto.getUserGuid() + " does not exist in Pepper";
            LOG.error(errMsg);
        }
        if (userDto.getAuth0UserId() == null) {
            errMsg = "User " + userDto.getUserGuid() + " is not associated with the Auth0 user " + userDto.getAuth0UserId();
        }
        if (errMsg != null) {
            throw new DDPException(errMsg);
        }
    }

    private void updateEmailInAuth0(Handle handle, ManagementAPI mgmtAPI, UserDto userDto, String email, String userGuid) {
        Auth0CallResponse status = Auth0Util.updateUserEmail(mgmtAPI, userDto, email);
        String errMsg = null;
        switch (status.getAuth0Status()) {
            case SUCCESS:
                handle.attach(DataExportDao.class).queueDataSync(userDto.getUserId(), true);
                LOG.info("The email of the user {} was successfuly changed", userGuid);
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
                errMsg = "An unknown problem happened in Pepper or Auth0.";
                if (status.getErrorMessage() != null) {
                    errMsg = errMsg + " Auth0 message: " + status.getErrorMessage();
                }
                break;
            default:
                errMsg = "The returned Auth0 call status is unknown - something completely unexpected happened";
        }
        if (errMsg != null) {
            throw new DDPException(errMsg);
        }
    }

    private User getUserDataFromAuth0(ManagementAPI mgmtAPI, String userGuid) {
        try {
            return mgmtAPI.users().get(userGuid, null).execute();
        } catch (Auth0Exception e) {
            ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND_IN_AUTH0, String.format(
                    "Auth0User not found for user %s ", userGuid));
            throw new DDPException(err.getMessage(), e);
        }
    }
}

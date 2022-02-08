package org.broadinstitute.ddp.route;

import com.auth0.client.mgmt.ManagementAPI;

import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;
import org.broadinstitute.ddp.json.auth0.UpdateUserPasswordRequestPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.UpdateUserLoginDataUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Request;
import spark.Response;

/**
 * This route updates the user password in Auth0
 */
public class UpdateUserPasswordRoute extends ValidatedJsonInputRoute<UpdateUserPasswordRequestPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateUserPasswordRoute.class);

    @Override
    public Object handle(Request request, Response response, UpdateUserPasswordRequestPayload requestPayload) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        Auth0CallResponse status = TransactionWrapper.withTxn(
                handle -> {
                    UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
                    UpdateUserLoginDataUtil.validateUserForLoginDataUpdateEligibility(userDto, response);

                    ManagementAPI mgmtAPI = Auth0Util.getManagementApiInstanceForUser(userDto.getUserGuid(), handle);
                    //get auth0 username or email to verify credentials
                    User auth0User = null;
                    try {
                        auth0User = mgmtAPI.users().get(userDto.getAuth0UserId(), null).execute();
                    } catch (Auth0Exception e) {
                        ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND_IN_AUTH0, String.format(
                                "Auth0User not found for user %s ", userGuid));
                        LOG.error(err.getMessage(), e);
                        throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
                    }

                    DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
                    String currentPassword = requestPayload.getCurrentPassword();
                    boolean isCredentialsValid = Auth0Util.isUserCredentialsValid(userGuid,
                            auth0User.getName(), currentPassword, ddpAuth.getClient(), handle);
                    if (!isCredentialsValid) {
                        ApiError err = new ApiError(ErrorCodes.INVALID_AUTH0_USER_CREDENTIALS, String.format(
                                "Invalid credentials for user %s ", userGuid));
                        LOG.warn("Invalid credentials for user: {} ", userGuid);
                        throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY, err);
                    }

                    String newPassword = requestPayload.getPassword();
                    LOG.info("Attempting to change the password of the user {}", userGuid);
                    return Auth0Util.updateUserPassword(mgmtAPI, userDto, newPassword);
                }
        );

        String errMsg = null;
        switch (status.getAuth0Status()) {
            case SUCCESS:
                LOG.info("The password of the user {} was successfuly changed", userGuid);
                response.status(204);
                return "";
            case INVALID_TOKEN:
                errMsg = "The provided Auth0 token is invalid";
                LOG.error(errMsg);
                throw ResponseUtil.haltError(response, 401, new ApiError(ErrorCodes.INVALID_TOKEN, errMsg));
            case PASSWORD_TOO_WEAK:
                errMsg = "The new password is too weak and was rejected by AUTH0";
                LOG.error(errMsg);
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.PASSWORD_TOO_WEAK, errMsg));
            case UNKNOWN_PROBLEM:
                errMsg = "An unknown problem happened in Pepper or Auth0.";
                if (status.getErrorMessage() != null) {
                    errMsg = errMsg + " Auth0 message: " + status.getErrorMessage();
                }
                LOG.error(errMsg);
                throw ResponseUtil.haltError(response, 500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
            default:
                errMsg = "The returned Auth0 call status is unknown - something completely unexpected happened";
                LOG.error(errMsg);
                throw new DDPException(errMsg);
        }
    }

}

package org.broadinstitute.ddp.route;

import com.auth0.client.mgmt.ManagementAPI;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.auth0.Auth0CallResponse;
import org.broadinstitute.ddp.json.auth0.UpdateUserEmailRequestPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.UpdateUserLoginDataUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

/**
 *  This route updates the user's email in Auth0
 */
@Slf4j
public class UpdateUserEmailRoute extends ValidatedJsonInputRoute<UpdateUserEmailRequestPayload> {
    @Override
    public Object handle(Request request, Response response, UpdateUserEmailRequestPayload requestPayload) {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        String newEmail = requestPayload.getEmail();
        return TransactionWrapper.withTxn(handle -> {
            UserDto userDto = handle.attach(JdbiUser.class).findByUserGuid(userGuid);
            UpdateUserLoginDataUtil.validateUserForLoginDataUpdateEligibility(userDto, response);

            log.info("Attempting to change the email of the user {}", userGuid);
            ManagementAPI mgmtAPI = Auth0Util.getManagementApiInstanceForUser(userDto.getUserGuid(), handle);
            // todo: investigate if this route should set email as verified or not
            Auth0CallResponse status = Auth0Util.updateUserEmail(mgmtAPI, userDto, newEmail, null);

            String errMsg;
            switch (status.getAuth0Status()) {
                case SUCCESS:
                    handle.attach(DataExportDao.class).queueDataSync(userDto.getUserId(), true);
                    log.info("The email of the user {} was successfuly changed", userGuid);
                    response.status(204);
                    return "";
                case INVALID_TOKEN:
                    errMsg = "The provided Auth0 token is invalid";
                    log.error(errMsg);
                    throw ResponseUtil.haltError(response, 401, new ApiError(ErrorCodes.INVALID_TOKEN, errMsg));
                case MALFORMED_EMAIL:
                    errMsg = "The new email " + newEmail + " is malformed and was rejected by Auth0";
                    log.error(errMsg);
                    throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.MALFORMED_EMAIL, errMsg));
                case EMAIL_ALREADY_EXISTS:
                    errMsg = "The new email " + newEmail + " already exists in Auth0, please choose another";
                    log.error(errMsg);
                    throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS, errMsg));
                case UNKNOWN_PROBLEM:
                    errMsg = "An unknown problem happened in Pepper or Auth0.";
                    if (status.getErrorMessage() != null) {
                        errMsg = errMsg + " Auth0 message: " + status.getErrorMessage();
                    }
                    log.error(errMsg);
                    throw ResponseUtil.haltError(response, 500, new ApiError(ErrorCodes.SERVER_ERROR, errMsg));
                default:
                    errMsg = "The returned Auth0 call status is unknown - something completely unexpected happened";
                    log.error(errMsg);
                    throw new DDPException(errMsg);
            }
        });
    }
}

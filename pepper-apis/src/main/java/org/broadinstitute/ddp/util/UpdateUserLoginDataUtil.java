package org.broadinstitute.ddp.util;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spark.Response;

/**
 * This class contains the logic common to routes updating the user login data
 */
public class UpdateUserLoginDataUtil {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateUserLoginDataUtil.class);

    /**
     * Checks if the user is eligible to login data update and halts with an error if not
     * @param userDto A user DTO to check
     * @param response A Spark Java response object
     */
    public static void validateUserForLoginDataUpdateEligibility(UserDto userDto, Response response) {
        if (userDto == null) {
            String errMsg = "User " + userDto.getUserGuid() + " does not exist in Pepper";
            LOG.error(errMsg);
            throw ResponseUtil.haltError(
                    response,
                    404,
                    new ApiError(ErrorCodes.USER_NOT_FOUND, errMsg)
            );
        }
        if (userDto.getAuth0UserId() == null) {
            String errMsg = "User " + userDto.getUserGuid() + " is not associated with the Auth0 user " + userDto.getAuth0UserId();
            LOG.error(errMsg);
            throw ResponseUtil.haltError(
                    response,
                    403,
                    new ApiError(ErrorCodes.USER_NOT_ASSOCIATED_WITH_AUTH0_USER, errMsg)
            );
        }
    }

}

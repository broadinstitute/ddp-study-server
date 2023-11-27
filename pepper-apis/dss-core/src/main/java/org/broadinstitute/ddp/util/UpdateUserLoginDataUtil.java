package org.broadinstitute.ddp.util;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.errors.ApiError;

import spark.Response;

/**
 * This class contains the logic common to routes updating the user login data
 */
@Slf4j
public class UpdateUserLoginDataUtil {
    /**
     * Checks if the user is eligible to login data update and halts with an error if not
     * @param userDto A user DTO to check
     * @param response A Spark Java response object
     */
    public static void validateUserForLoginDataUpdateEligibility(UserDto userDto, Response response) {
        if (userDto == null) {
            String errMsg = "User was not found";
            log.warn(errMsg);
            throw ResponseUtil.haltError(
                    response,
                    404,
                    new ApiError(ErrorCodes.USER_NOT_FOUND, errMsg)
            );
        }

        if (userDto.getAuth0UserId().isEmpty()) {
            String errMsg = "User " + userDto.getUserGuid() + " is not associated with an Auth0 account";
            log.warn(errMsg);
            throw ResponseUtil.haltError(
                    response,
                    403,
                    new ApiError(ErrorCodes.USER_NOT_ASSOCIATED_WITH_AUTH0_USER, errMsg)
            );
        }
    }

}

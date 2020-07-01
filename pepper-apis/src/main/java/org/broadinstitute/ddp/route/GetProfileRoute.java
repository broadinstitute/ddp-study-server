package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetProfileRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetProfileRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Retrieving profile for user with guid {}", userGuid);

        return TransactionWrapper.withTxn((handle) -> {
            UserProfile profile = handle.attach(UserProfileDao.class)
                    .findProfileByUserGuid(userGuid).orElse(null);
            if (profile != null) {
                return new Profile(profile);    // Convert to json view.
            } else {
                String errorMsg = "Profile not found for user with guid: " + userGuid;
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.MISSING_PROFILE, errorMsg));
            }
        });
    }
}

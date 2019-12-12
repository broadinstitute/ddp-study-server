package org.broadinstitute.ddp.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.json.Profile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetProfileRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetProfileRoute.class);
    private final UserDao userDao;

    public GetProfileRoute(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String guid = request.params(RouteConstants.PathParam.USER_GUID);
        LOG.info("Retrieving profile for {}", guid);

        if (StringUtils.isBlank(guid)) {
            ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_USER_GUID);
        }

        Profile profile = TransactionWrapper.withTxn((handle) -> {
            if (userDao.doesProfileExist(handle, guid)) {
                return userDao.getProfile(handle, guid);
            } else {
                ResponseUtil.halt400ErrorResponse(response, ErrorCodes.MISSING_PROFILE);
            }
            return null;
        });
        return profile;
    }
}

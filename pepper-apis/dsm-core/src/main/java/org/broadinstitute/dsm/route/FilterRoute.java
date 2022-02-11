package org.broadinstitute.dsm.route;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.model.filter.FilterFactory;
import org.broadinstitute.dsm.model.filter.Filterable;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class FilterRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String parent = queryParams.get(DBConstants.FILTER_PARENT).value();
        if (StringUtils.isBlank(parent)) throw new IllegalArgumentException("parent cannot be empty");

        String realm = queryParams.get(RoutePath.REALM).value();
        if (StringUtils.isBlank(realm)) throw new IllegalArgumentException("realm cannot be empty");

        String userIdRequest = queryParams.get(UserUtil.USER_ID).value();
        if (!userId.equals(userIdRequest)) throw new IllegalAccessException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);

        if (!UserUtil.checkUserAccess(realm, userId, "mr_view", userIdRequest) && !UserUtil.checkUserAccess(realm, userId, "pt_list_view", userIdRequest)) {
            response.status(500);
            return UserErrorMessages.NO_RIGHTS;
        }
        Filterable filterable = FilterFactory.of(request);
        return filterable.filter(queryParams);
    }
}

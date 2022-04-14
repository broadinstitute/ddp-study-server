package org.broadinstitute.dsm.route;

import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

public class AllowedRealmsRoute extends RequestHandler {

    private static final String MENU = "menu";

    @Override
    public Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String userIdRequest = UserUtil.getUserId(request);
        if (!userId.equals(userIdRequest)) {
            throw new RuntimeException("User id was not equal. User Id in token " + userId + " user Id in request " + userIdRequest);
        }
        if (request.url().contains(RoutePath.STUDIES)) {
            return UserUtil.getAllowedStudies(userIdRequest);
        }

        if (queryParams.value(MENU) != null) {
            String menu = queryParams.get(MENU).value();
            return UserUtil.getListOfAllowedRealms(userIdRequest, menu);
        }

        return UserUtil.getListOfAllowedRealms(userIdRequest);
    }
}

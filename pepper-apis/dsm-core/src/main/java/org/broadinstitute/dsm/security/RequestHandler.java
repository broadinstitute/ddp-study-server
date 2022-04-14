package org.broadinstitute.dsm.security;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.util.SecurityUtil;
import spark.Request;
import spark.Response;
import spark.Route;

public abstract class RequestHandler implements Route {

    @Override
    public Object handle(Request request, Response response) throws Exception {
        //get the token if it is there
        String userId = SecurityUtil.getUserId(request);
        if (StringUtils.isNotBlank(userId)) {
            return processRequest(request, response, userId);
        }
        throw new RuntimeException("Error user_id was missing from token");
    }

    protected abstract Object processRequest(Request request, Response response, String userId) throws Exception;
}

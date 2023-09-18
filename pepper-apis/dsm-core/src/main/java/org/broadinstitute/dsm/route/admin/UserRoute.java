package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.route.RouteUtil;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.UpdateUserRequest;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.service.admin.UserRequest;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class UserRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {
        String studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        String body = RouteUtil.requireRequestBody(request);
        UserAdminService adminService = new UserAdminService(userId, studyGroup);

        String requestMethod = request.requestMethod();
        if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            UserRequest req;
            try {
                req = new Gson().fromJson(body, UserRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                throw new DSMBadRequestException("Invalid request format");
            }
            adminService.addAndRemoveUsers(req);
        } else if (requestMethod.equals(RoutePath.RequestMethod.PUT.toString())) {
            UpdateUserRequest req;
            try {
                req = new Gson().fromJson(body, UpdateUserRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                throw new DSMBadRequestException("Invalid request format");
            }
            adminService.updateUser(req);
        } else {
            RouteUtil.handleInvalidRouteMethod(request, "UserRoute");
            return null;
        }

        return new Result(200);
    }
}

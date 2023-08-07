package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
        String studyGroup;
        try {
            studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        } catch (Exception e) {
            return UserRoleRoute.handleError(e, "getting study group", response);
        }

        String body = request.body();
        if (StringUtils.isBlank(body)) {
            response.status(400);
            return "Request body is blank";
        }

        UserAdminService adminService = new UserAdminService(userId, studyGroup);
        String requestMethod = request.requestMethod();

        if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            UserRequest req;
            try {
                req = new Gson().fromJson(body, UserRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                response.status(400);
                return "Invalid request format";
            }
            try {
                adminService.addAndRemoveUsers(req);
            } catch (Exception e) {
                return UserRoleRoute.handleError(e, "adding user", response);
            }
        } else if (requestMethod.equals(RoutePath.RequestMethod.PUT.toString())) {
            UpdateUserRequest req;
            try {
                req = new Gson().fromJson(body, UpdateUserRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                response.status(400);
                return "Invalid request format";
            }
            try {
                adminService.updateUser(req);
            } catch (Exception e) {
                return UserRoleRoute.handleError(e, "updating user", response);
            }
        } else {
            String msg = "Invalid HTTP method for UserRoute: " + requestMethod;
            log.error(msg);
            response.status(500);
            return msg;
        }

        return new Result(200);
    }
}

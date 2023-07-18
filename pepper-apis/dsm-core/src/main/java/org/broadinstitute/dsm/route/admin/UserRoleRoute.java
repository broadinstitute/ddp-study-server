package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.UserRequest;
import org.broadinstitute.dsm.service.admin.UserRoleRequest;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class UserRoleRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {
        String studyGroup;
        try {
            studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        } catch (Exception e) {
            return handleError(e, "getting study group", response);
        }

        UserAdminService service = new UserAdminService(userId, studyGroup);

        String requestMethod = request.requestMethod();
        String body = request.body();
        boolean hasBody = !StringUtils.isBlank(body);

        if (requestMethod.equals(RoutePath.RequestMethod.GET.toString())) {
            UserRequest req = null;
            if (hasBody) {
                try {
                    req = new Gson().fromJson(body, UserRequest.class);
                } catch (Exception e) {
                    log.info("Invalid request format for {}", body);
                    response.status(400);
                    return "Invalid request format";
                }
            }
            try {
                return service.getUserRoles(req);
            } catch (Exception e) {
                return handleError(e, "getting user roles", response);
            }
        }

        if (!hasBody) {
            response.status(400);
            return "Request body is blank";
        }

        UserRoleRequest req;
        try {
            req = new Gson().fromJson(body, UserRoleRequest.class);
        } catch (Exception e) {
            log.info("Invalid request format for {}", body);
            response.status(400);
            return "Invalid request format";
        }

        if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            try {
                service.addUserRoles(req);
            } catch (Exception e) {
                return handleError(e, "adding user roles", response);
            }
        } else if (requestMethod.equals(RoutePath.RequestMethod.DELETE.toString())) {
            try {
                service.removeUserRoles(req);
            } catch (Exception e) {
                return handleError(e, "removing user roles", response);
            }
        } else {
            String msg = "Invalid HTTP method for UserRoleRoute: " + requestMethod;
            log.error(msg);
            response.status(500);
            return msg;
        }

        return new Result(200);
    }

    protected static String handleError(Throwable e, String operation, Response response) {
        if (e instanceof DSMBadRequestException) {
            response.status(400);
            log.info("DSMBadRequestException {}: {}", operation, e.getMessage());
            return e.getMessage();
        } else if (e instanceof DsmInternalError) {
            log.error("Error {}: {}", operation, e.getMessage());
            response.status(500);
            return "Internal error. Contact development team";
        }

        // any other exception
        log.error("Error {}: {}", operation, e.getMessage());
        response.status(500);
        return e.getMessage();
    }
}

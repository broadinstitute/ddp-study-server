package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.route.RouteUtil;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.SetUserRoleRequest;
import org.broadinstitute.dsm.service.admin.UpdateUserRoleRequest;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.service.admin.UserRoleRequest;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class UserRoleRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {
        String studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        UserAdminService service = new UserAdminService(userId, studyGroup);

        String requestMethod = request.requestMethod();
        String body = request.body();
        boolean hasBody = !StringUtils.isBlank(body);

        if (requestMethod.equals(RoutePath.RequestMethod.GET.toString())) {
            return getUserRoles(request.body(), response, service);
        }

        if (!hasBody) {
            response.status(400);
            return "Request body is blank";
        }

        if (requestMethod.equals(RoutePath.RequestMethod.PUT.toString())) {
            SetUserRoleRequest req;
            try {
                req = new Gson().fromJson(body, SetUserRoleRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                throw new DSMBadRequestException("Invalid request format");
            }
            service.setUserRoles(req);
        } else if (requestMethod.equals(RoutePath.RequestMethod.POST.toString())) {
            UpdateUserRoleRequest req;
            try {
                req = new Gson().fromJson(body, UpdateUserRoleRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                throw new DSMBadRequestException("Invalid request format");
            }
            service.updateUserRoles(req);
        } else {
            RouteUtil.handleInvalidRouteMethod(request, "UserRoleRoute");
            return null;
        }
        return new Result(200);
    }

    protected static Object getUserRoles(String body, Response response, UserAdminService service) {
        UserRoleRequest req = null;
        if (!StringUtils.isBlank(body)) {
            try {
                req = new Gson().fromJson(body, UserRoleRequest.class);
            } catch (Exception e) {
                log.info("Invalid request format for {}", body);
                throw new DSMBadRequestException("Invalid request format");
            }
        }
        return service.getUserRoles(req);
    }
}

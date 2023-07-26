package org.broadinstitute.dsm.route.admin;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.StudyRoleResponse;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class StudyRoleRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {
        String studyGroup;
        try {
            studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        } catch (Exception e) {
            return UserRoleRoute.handleError(e, "getting study group", response);
        }

        UserAdminService adminService = new UserAdminService(userId, studyGroup);

        if (!request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())) {
            String msg = "Invalid HTTP method for UserRoleRoute: " + request.requestMethod();
            log.error(msg);
            response.status(500);
            return msg;
        }

        try {
            return adminService.getStudyRoles();
        } catch (Exception e) {
            return UserRoleRoute.handleError(e, "getting study roles", response);
        }
    }
}

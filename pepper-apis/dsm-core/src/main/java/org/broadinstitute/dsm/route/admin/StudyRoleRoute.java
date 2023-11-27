package org.broadinstitute.dsm.route.admin;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.route.RouteUtil;
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
        String studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());
        UserAdminService adminService = new UserAdminService(userId, studyGroup);

        if (!request.requestMethod().equals(RoutePath.RequestMethod.GET.toString())) {
            RouteUtil.handleInvalidRouteMethod(request, "StudyRoleRoute");
            return null;
        }

        return adminService.getStudyRoles();
    }
}

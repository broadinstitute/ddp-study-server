package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.dsm.service.admin.AddUserRequest;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class UserRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {

        String studyGroup = UserAdminService.getStudyGroup(request.queryMap().toMap());

        String body = request.body();
        if (StringUtils.isBlank(body)) {
            response.status(400);
            return "Request body is blank";
        }

        AddUserRequest req;
        try {
            req = new Gson().fromJson(body, AddUserRequest.class);
        } catch (Exception e) {
            log.info("Invalid request format for {}", body);
            response.status(400);
            return "Invalid request format";
        }

        UserAdminService adminService = new UserAdminService(userId, studyGroup);

        if (request.requestMethod().equals(RoutePath.RequestMethod.POST.toString())) {
            try {
                adminService.createUser(req);
            } catch (DSMBadRequestException e) {
                response.status(400);
                return e.getMessage();
            } catch (DsmInternalError e) {
                log.error("Error adding user: {}", e.getMessage());
                response.status(500);
                return "Internal error. Contact development team";
            } catch (Exception e) {
                log.error("Error adding user: {}", e.getMessage());
                response.status(500);
                return e.getMessage();
            }
        } else if (request.requestMethod().equals(RoutePath.RequestMethod.DELETE.toString())) {
            try {
                adminService.removeUser(req);
            } catch (DSMBadRequestException e) {
                response.status(400);
                return e.getMessage();
            } catch (DsmInternalError e) {
                log.error("Error removing user: {}", e.getMessage());
                response.status(500);
                return "Internal error. Contact development team";
            } catch (Exception e) {
                log.error("Error removing user: {}", e.getMessage());
                response.status(500);
                return e.getMessage();
            }
        } else {
            String msg = "Invalid HTTP method for UserRoute";
            log.error(msg);
            response.status(500);
            return msg;
        }

        return new Result(200);
    }
}

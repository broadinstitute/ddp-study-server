package org.broadinstitute.dsm.route.admin;

import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.exception.DSMBadRequestException;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.service.admin.AddUserRoleRequest;
import org.broadinstitute.dsm.service.admin.UserAdminService;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.Request;
import spark.Response;

@Slf4j
public class AddUserRoleRoute extends RequestHandler {

    @Override
    public Object processRequest(Request request, Response response, String userId) {

        String body = request.body();
        if (StringUtils.isBlank(body)) {
            response.status(400);
            return "Request body is blank";
        }

        AddUserRoleRequest req;
        try {
            req = new Gson().fromJson(body, AddUserRoleRequest.class);
            log.info("TEMP: AddUserRoleRequest {}", req);
        } catch (Exception e) {
            log.info("Invalid request format for {}", body);
            response.status(400);
            return "Invalid request format";
        }

        UserAdminService adminService = new UserAdminService(userId);

        try {
            adminService.addUserToRoles(req);
        } catch (DSMBadRequestException e) {
            response.status(400);
            return e.getMessage();
        } catch (DsmInternalError e) {
            log.error("Error adding user to role: {}", e.getMessage());
            response.status(500);
            return "Internal error. Contact development team";
        } catch (Exception e) {
            log.error("Error adding user to role: {}", e.getMessage());
            response.status(500);
            return e.getMessage();
        }

        return new Result(200);
    }
    /*
    SELECT @uemail := "<someone's email>";

SELECT @uid := user_id FROM access_user WHERE email COLLATE utf8mb4_general_ci  = @uemail;


SELECT @gid := group_id from ddp_group where name COLLATE utf8mb4_general_ci = "pecgs" ;

INSERT INTO `dev_dsm_db`.`access_user_role_group` (`user_id`, `role_id`, `group_id`)
 SELECT @uid, role_id, @gid FROM access_role WHERE name in ('onc_history_upload', 'upload_ror_file');
     */

}

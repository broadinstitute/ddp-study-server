package org.broadinstitute.dsm.route.user;

import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.db.dao.roles.UserRoleDao;
import org.broadinstitute.dsm.db.dto.user.UserRoleDto;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import org.broadinstitute.lddp.handlers.util.Result;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class ModifyUserRoute extends RequestHandler {

    UserRoleDao userRoleDao;

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        } else {
            throw new RuntimeException("No realm query param was sent");
        }

        if (StringUtils.isNotBlank(realm)) {
            if (RoutePath.RequestMethod.POST.toString().equals(request.requestMethod())) {
                if (UserUtil.checkUserAccess(realm, userId, DBConstants.USER_ACCESS, null)) {
                    String requestBody = request.body();
                    UserRoleDto modifiedUser = new Gson().fromJson(requestBody, UserRoleDto.class);

                    log.info("Going to update info for user id " + modifiedUser.getUser().getUserId() + " wit new role "
                            + modifiedUser.getRole().getRoleId());
                    userRoleDao.modifyUser(modifiedUser, realm);

                    return new Result(200);
                } else {
                    response.status(401);
                    return new Result(401, UserErrorMessages.NO_RIGHTS);
                }
            }
        }
        log.error("realm is empty");
        response.status(500);
        return new Result(500, UserErrorMessages.CONTACT_DEVELOPER);
    }

}

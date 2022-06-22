package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import java.io.IOException;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.UserDeleteService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.jdbi.v3.core.Handle;
import spark.Request;
import spark.Response;
import spark.Route;

@Slf4j
@AllArgsConstructor
public class DeleteUserRoute implements Route {
    private final UserDeleteService userDeleteService;

    @Override
    public Object handle(Request request, Response response) throws IOException {
        String userGuid = request.params(USER_GUID);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();

        log.info("Trying to delete user with GUID: {}, operator GUID: {}", userGuid, operatorGuid);

        if (userGuid.equals(operatorGuid)) {
            String message = "Users cannot delete themselves";
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        return TransactionWrapper.withTxn(handle -> {
            UserDao userDao = handle.attach(UserDao.class);
            User user = userDao.findUserByGuid(userGuid)
                    .orElseThrow(() -> ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                            new ApiError(ErrorCodes.USER_NOT_FOUND,
                                    "User with guid '" + userGuid + "' was not found")));
            CheckError err = checkLimits(handle, user, operatorGuid);
            if (err != null) {
                throw ResponseUtil.haltError(response, err.getStatus(), err.getError());
            }
            userDeleteService.simpleDelete(handle, user, "operatorGuid=" + operatorGuid, "called from DELETE /user");
            response.status(HttpStatus.SC_NO_CONTENT);
            return "";
        });
    }

    CheckError checkLimits(Handle handle, User user, String operatorGuid) {
        String userGuid = user.getGuid();

        // Only the proxy user is allowed to delete one of their managed user
        UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
        if (userGovernanceDao.findActiveGovernancesByProxyGuid(operatorGuid)
                .noneMatch(gov -> userGuid.equals(gov.getGovernedUserGuid()))) {
            String message = "User with guid '" + userGuid
                    + "' is not governed by current user.";
            return new CheckError(HttpStatus.SC_UNAUTHORIZED,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        // The user shouldn't be governed by others
        if (userGovernanceDao.findGovernancesByParticipantGuid(userGuid)
                .anyMatch(gov -> !operatorGuid.equals(gov.getProxyUserGuid()))) {
            String message = "User with guid '" + userGuid
                    + "' is also governed by another user.";
            return new CheckError(HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        // The user to be deleted cannot have an Auth0 account
        if (user.hasAuth0Account()) {
            String message = "User with guid '" + userGuid
                    + "' has auth0 account associated. Deleting of such users is not supported.";
            return new CheckError(HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        // The user cannot yet have reached the ENROLLED status
        if (handle.attach(JdbiUserStudyEnrollment.class).findByUserGuid(userGuid).stream().anyMatch(
                enrollment -> enrollment.getEnrollmentStatus().isEnrolled())) {
            String message = "User with guid '" + userGuid
                    + "' has at least one enrollment completed. Deleting of such users is not supported.";
            return new CheckError(HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        // The user shouldn't have any kit request
        if (CollectionUtils.isNotEmpty(handle.attach(DsmKitRequestDao.class).findKitRequestIdsByParticipantId(user.getId()))) {
            String message = "User with guid '" + userGuid
                    + "' has a kit request. Deleting of such users is not supported.";
            return new CheckError(HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        return null;
    }

    static class CheckError {
        private final int status;
        private final ApiError error;

        public CheckError(int status, ApiError error) {
            this.status = status;
            this.error = error;
        }

        public int getStatus() {
            return status;
        }

        public ApiError getError() {
            return error;
        }
    }
}

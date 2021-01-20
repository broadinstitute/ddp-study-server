package org.broadinstitute.ddp.route;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.UserService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

import java.util.Collections;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

public class DeleteUserRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(DeleteUserRoute.class);

    private final UserService userService;

    public DeleteUserRoute(UserService userService) {
        this.userService = userService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String userGuid = request.params(USER_GUID);
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String operatorGuid = ddpAuth.getOperator();

        LOG.info("Trying to delete user with GUID: {}, operator GUID: {}", userGuid, operatorGuid);

        if (userGuid.equals(operatorGuid)) {
            String message = "Users cannot delete themselves";
            throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                    new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                            message));
        }

        return TransactionWrapper.withTxn(handle -> {
            UserDao userDao = handle.attach(UserDao.class);

            // Only the proxy user is allowed to delete one of their managed user
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            if (userGovernanceDao.findActiveGovernancesByProxyGuid(operatorGuid)
                    .noneMatch(gov -> userGuid.equals(gov.getGovernedUserGuid()))) {
                String message = "User with guid '" + userGuid
                        + "' is not governed by current user.";
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNAUTHORIZED,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                                message));
            }

            // The user shouldn't be governed by others
            if (userGovernanceDao.findGovernancesByParticipantGuid(userGuid)
                    .anyMatch(gov -> !operatorGuid.equals(gov.getProxyUserGuid()))) {
                String message = "User with guid '" + userGuid
                        + "' is also governed by another user.";
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                                message));
            }

            User user = userDao.findUserByGuid(userGuid)
                    .orElseThrow(() -> ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND,
                            new ApiError(ErrorCodes.USER_NOT_FOUND,
                                    "User with guid '" + userGuid + "' was not found")));

            // The user to be deleted cannot have an Auth0 account
            if (user.getAuth0UserId() != null) {
                String message = "User with guid '" + userGuid
                        + "' has auth0 account associated. Deleting of such users is not supported.";
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                                message));
            }

            // The user cannot yet have reached the ENROLLED status
            if (handle.attach(JdbiUserStudyEnrollment.class).findByUserGuid(userGuid).stream().anyMatch(
                    enrollment -> EnrollmentStatusType.ENROLLED.equals(enrollment.getEnrollmentStatus()))) {
                String message = "User with guid '" + userGuid
                        + "' has at least one enrollment completed. Deleting of such users is not supported.";
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                                message));
            }

            // The user shouldn't have any kit request
            if (CollectionUtils.isNotEmpty(handle.attach(DsmKitRequestDao.class).findKitRequestIdsByParticipantId(user.getId()))) {
                String message = "User with guid '" + userGuid
                        + "' has a kit request. Deleting of such users is not supported.";
                throw ResponseUtil.haltError(response, HttpStatus.SC_UNPROCESSABLE_ENTITY,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED,
                                message));
            }

            LOG.info("Deleting user with GUID: {}", userGuid);
            userService.deleteUser(handle, user.getId());
            LOG.info("User with GUID: {} deleted", userGuid);
            response.status(HttpStatus.SC_NO_CONTENT);
            return "";
        });
    }
}

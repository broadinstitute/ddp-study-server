package org.broadinstitute.ddp.route;

import com.auth0.json.mgmt.Connection;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.CreateUserLoginAccountPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.event.LoginAccountCreatedSignal;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class AdminCreateUserLoginAccountRoute extends ValidatedJsonInputRoute<CreateUserLoginAccountPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(AdminCreateUserLoginAccountRoute.class);

    @Override
    protected Class<CreateUserLoginAccountPayload> getTargetClass(Request request) {
        return CreateUserLoginAccountPayload.class;
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CreateUserLoginAccountPayload payload) {
        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String userGuid = request.params(RouteConstants.PathParam.USER_GUID);

        LOG.info("Attempting to create login account for user {} in study {} by operator {}",
                userGuid, studyGuid, ddpAuth.getOperator());

        response.type(ContentType.APPLICATION_JSON.getMimeType());
        TransactionWrapper.useTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            var userDao = handle.attach(UserDao.class);
            User user = userDao.findUserByGuid(userGuid).orElse(null);
            if (user == null) {
                String msg = "Could not find user with guid " + userGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user.getId(), studyDto.getId())
                    .orElse(null);
            if (status == null) {
                LOG.error("Attempted to create login account for user not in study, userGuid={}, studyGuid={}", userGuid, studyGuid);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, "User not in study"));
            } else if (status.isExited()) {
                LOG.error("Attempted to create login account for exited user, userGuid={}, studyGuid={}", userGuid, studyGuid);
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, "User not in study"));
            } else {
                LOG.info("Found user {} in study {} with status {}", userGuid, studyGuid, status);
            }

            if (user.hasAuth0Account()) {
                LOG.error("Attempted to create login account for user with an existing auth0 account, userGuid={}, studyGuid={}",
                        userGuid, studyGuid);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, "User already has login account"));
            } else if (user.isTemporary()) {
                LOG.error("Attempted to create login account for temporary user, userGuid={}, studyGuid={}", userGuid, studyGuid);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, "User is a temporary user"));
            }

            Auth0ManagementClient auth0Mgmt = createManagementClient(handle, studyGuid);
            String auth0ClientId = payload.getAuth0ClientId();

            // Find database connection to sign up new user
            var listResult = auth0Mgmt.listClientConnections(auth0ClientId);
            if (listResult.hasThrown() || listResult.hasError()) {
                Exception e = listResult.hasThrown() ? listResult.getThrown() : listResult.getError();
                LOG.warn("Error getting client connections for auth0 client {}", auth0ClientId, e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up client"));
            }

            Connection dbConn = listResult.getBody().stream()
                    .filter(conn -> conn.getStrategy().equals(Auth0ManagementClient.DB_CONNECTION_STRATEGY))
                    .findFirst()
                    .orElse(null);
            if (dbConn == null) {
                LOG.error("Client {} does not have database connection", auth0ClientId);
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, "Client has no database connection"));
            } else {
                LOG.info("Using database connection with name {} for client {}", dbConn.getName(), auth0ClientId);
            }

            // Create auth0 user
            String email = payload.getEmail();
            String randomPassword = GuidUtils.randomPassword();
            var userResult = auth0Mgmt.createAuth0User(dbConn.getName(), email, randomPassword);
            if (userResult.hasThrown() || userResult.hasError()) {
                Exception e = userResult.hasThrown() ? userResult.getThrown() : userResult.getError();
                LOG.error("Error creating new auth0 account for user {}", user.getGuid(), e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error setting up login account"));
            } else {
                LOG.info("Created auth0 account for user {}", user.getGuid());
            }

            // Link auth0 user id
            var auth0User = userResult.getBody();
            userDao.assignAuth0UserId(user.getGuid(), auth0User.getId());
            LOG.info("Assigned auth0 user id to user {}", user.getGuid());

            // Create password reset ticket
            var ticketResult = auth0Mgmt.createPasswordResetTicket(auth0User.getId(), payload.getRedirectUrl());
            if (ticketResult.hasThrown() || ticketResult.hasError()) {
                Exception e = ticketResult.hasThrown() ? ticketResult.getThrown() : ticketResult.getError();
                LOG.error("Error creating password reset ticket for user {}", user.getGuid(), e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error setting up login account"));
            } else {
                LOG.info("Created password reset ticket for user {}", user.getGuid());
            }

            // Run downstream study events
            User operator = userDao.findUserByGuid(ddpAuth.getOperator())
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + ddpAuth.getOperator()));
            var signal = new LoginAccountCreatedSignal(
                    operator.getId(),
                    user.getId(),
                    user.getGuid(),
                    studyDto.getId(),
                    ticketResult.getBody());
            triggerEvents(handle, signal);
        });

        response.status(HttpStatus.SC_CREATED);
        return null;
    }

    Auth0ManagementClient createManagementClient(Handle handle, String studyGuid) {
        Auth0TenantDto tenantDto = handle.attach(JdbiAuth0Tenant.class).findByStudyGuid(studyGuid);
        return new Auth0ManagementClient(
                tenantDto.getDomain(),
                tenantDto.getManagementClientId(),
                tenantDto.getManagementClientSecret());
    }

    void triggerEvents(Handle handle, LoginAccountCreatedSignal signal) {
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }
}

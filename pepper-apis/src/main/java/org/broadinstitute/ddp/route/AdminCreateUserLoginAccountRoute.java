package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.stream.Collectors;

import com.auth0.json.mgmt.Connection;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.admin.CreateUserLoginAccountPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.event.LoginAccountCreatedSignal;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.service.Auth0Service;
import org.broadinstitute.ddp.service.EventService;
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

            Auth0Service auth0Service = getAuth0Service(handle, studyGuid);

            // Figure out what client to use
            List<ClientDto> webClients = handle.attach(ClientDao.class)
                    .findAllPermittedClientsForStudy(studyDto.getId())
                    .stream()
                    .filter(client -> client.getWebPasswordRedirectUrl() != null)
                    .collect(Collectors.toList());
            if (webClients.size() != 1) {
                LOG.error("Expected to find one client with web password redirect url"
                                + " for study {} but found {}, unable to proceed with login account creation",
                        studyGuid, webClients.size());
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up client"));
            }
            ClientDto client = webClients.get(0);
            String auth0ClientId = client.getAuth0ClientId();
            String auth0Domain = client.getAuth0Domain();

            // Figure out client's database connection
            Connection dbConn;
            try {
                dbConn = auth0Service.findClientDBConnection(auth0ClientId);
            } catch (Exception e) {
                LOG.warn("Error getting client connections for auth0 client {}", auth0ClientId, e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up client"));
            }
            if (dbConn == null) {
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up client"));
            } else {
                LOG.info("Using database connection with name {} for client {}", dbConn.getName(), auth0ClientId);
            }

            // Construct redirect URL
            String redirectUrl;
            try {
                redirectUrl = auth0Service.generatePasswordResetRedirectUrl(auth0ClientId, auth0Domain);
            } catch (Exception e) {
                LOG.error("Error constructing password reset redirect URL for user {}", user.getGuid(), e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up client"));
            }
            LOG.info("User will be redirected here after password reset: {}", redirectUrl);

            // Create the login account
            Auth0Service.UserWithPasswordTicket userWithTicket;
            try {
                userWithTicket = auth0Service.createUserWithPasswordTicket(dbConn, payload.getEmail(), redirectUrl);
            } catch (Exception e) {
                LOG.error("Error creating auth0 account for user {}", user.getGuid(), e);
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        new ApiError(ErrorCodes.SERVER_ERROR, "Error setting up login account"));
            }
            if (userWithTicket == null) {
                LOG.warn("Email already exists in connection {}", dbConn.getName());
                throw ResponseUtil.haltError(HttpStatus.SC_BAD_REQUEST,
                        new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS, "Email already exists"));
            }

            // Link auth0 user id
            var auth0User = userWithTicket.getUser();
            userDao.assignAuth0UserId(user.getGuid(), auth0User.getId());
            LOG.info("Assigned auth0 user id to user {}", user.getGuid());

            // Run downstream study events
            User operator = userDao.findUserByGuid(ddpAuth.getOperator())
                    .orElseThrow(() -> new DDPException("Could not find operator with guid " + ddpAuth.getOperator()));
            var signal = new LoginAccountCreatedSignal(
                    operator.getId(),
                    user.getId(),
                    user.getGuid(),
                    studyDto.getId(),
                    userWithTicket.getTicket());
            triggerEvents(handle, signal);
        });

        response.status(HttpStatus.SC_CREATED);
        return null;
    }

    Auth0Service getAuth0Service(Handle handle, String studyGuid) {
        Auth0TenantDto tenantDto = handle.attach(JdbiAuth0Tenant.class).findByStudyGuid(studyGuid);
        return new Auth0Service(new Auth0ManagementClient(
                tenantDto.getDomain(),
                tenantDto.getManagementClientId(),
                tenantDto.getManagementClientSecret()));
    }

    void triggerEvents(Handle handle, LoginAccountCreatedSignal signal) {
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }
}

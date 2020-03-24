package org.broadinstitute.ddp.route;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.UserDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.CreateTemporaryUserPayload;
import org.broadinstitute.ddp.json.CreateTemporaryUserResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateTemporaryUserRoute extends ValidatedJsonInputRoute<CreateTemporaryUserPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateTemporaryUserRoute.class);

    private final UserDao userDao;

    public CreateTemporaryUserRoute(UserDao userDao) {
        this.userDao = userDao;
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CreateTemporaryUserPayload payload) {
        String auth0ClientId = payload.getAuth0ClientId();
        String auth0Domain = payload.getAuth0Domain();

        LOG.info(
                "Request to create new temporary user from ipAddress '{}', auth0ClientId '{}' and auth0Domain '{}'",
                request.ip(), auth0ClientId, auth0Domain
        );

        CreateTemporaryUserResponse result = TransactionWrapper.withTxn(handle -> {
            ClientDto clientDto = handle.attach(JdbiClient.class)
                    .getClientByAuth0ClientAndDomain(auth0ClientId, auth0Domain).orElse(null);
            if (clientDto == null || clientDto.isRevoked()) {
                String msg = String.format("Client '%s' is invalid or inactive", auth0ClientId);
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg));
            }

            try {
                UserDto userDto = userDao.createTemporaryUser(handle, auth0ClientId);
                return new CreateTemporaryUserResponse(userDto.getUserGuid(), userDto.getExpiresAtMillis());
            } catch (DaoException e) {
                String msg = "Error while creating temporary user";
                LOG.error(msg, e);
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, new ApiError(ErrorCodes.SERVER_ERROR, msg));
            }
        });

        LOG.info("Created new temporary user with userGuid '{}' and expiresAt '{}'", result.getUserGuid(), result.getExpiresAt());

        response.status(HttpStatus.SC_CREATED);
        return result;
    }
}

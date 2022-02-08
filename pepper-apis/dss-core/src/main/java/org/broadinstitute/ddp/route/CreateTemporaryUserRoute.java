package org.broadinstitute.ddp.route;

import java.util.List;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.db.dto.LanguageDto;
import org.broadinstitute.ddp.json.CreateTemporaryUserPayload;
import org.broadinstitute.ddp.json.CreateTemporaryUserResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateTemporaryUserRoute extends ValidatedJsonInputRoute<CreateTemporaryUserPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(CreateTemporaryUserRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, CreateTemporaryUserPayload payload) {
        String auth0ClientId = payload.getAuth0ClientId();
        String auth0Domain = payload.getAuth0Domain();
        String languageCode = payload.getLanguageCode();
        if (languageCode == null) {
            //fall back to default language
            languageCode = LanguageStore.DEFAULT_LANG_CODE;
        }
        LanguageDto languageDto = LanguageStore.get(languageCode);
        Long languageId = languageDto != null ? languageDto.getId() : null;

        LOG.info(
                "Request to create new temporary user from ipAddress '{}', auth0ClientId '{}' and auth0Domain '{}'",
                request.ip(), auth0ClientId, auth0Domain
        );

        CreateTemporaryUserResponse result = TransactionWrapper.withTxn(handle -> {
            ClientDto clientDto = null;
            boolean isDomainSpecified = auth0Domain != null && !auth0Domain.isBlank();
            if (isDomainSpecified) {
                clientDto = handle.attach(JdbiClient.class).getClientByAuth0ClientAndDomain(auth0ClientId, auth0Domain).orElse(null);
            } else {
                // Left for backward compatibility. It's expected that for some time
                // there will be no clashes between clients with the same Auth0 client id
                // When they start to occur, change the check accordingly
                LOG.info("Domain query parameter is missing, checking if the auth0 client id '{}' is unique", auth0ClientId);
                List<ClientDto> clientDtos = handle.attach(JdbiClient.class).getClientsByAuth0ClientId(auth0ClientId);
                if (clientDtos.size() > 1) {
                    String msg = String.format("Auth0 client id %s is not unique, please also provide auth0Domain property", auth0ClientId);
                    LOG.error(msg);
                    throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
                } else if (clientDtos.size() == 1) {
                    LOG.info("All fine, client id '{}' is unique, nothing to worry about", auth0ClientId);
                    clientDto = clientDtos.get(0);
                }
            }

            if (clientDto == null || clientDto.isRevoked()) {
                String msg = String.format("Client '%s' is invalid or inactive", auth0ClientId);
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, msg));
            }

            try {
                User user = handle.attach(UserDao.class).createTempUser(clientDto.getId());
                //create profile
                UserProfileDao profileDao = handle.attach(UserProfileDao.class);
                profileDao.createProfile(new UserProfile.Builder(user.getId())
                        .setPreferredLangId(languageId)
                        .build());

                return new CreateTemporaryUserResponse(user.getGuid(), user.getExpiresAt());
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

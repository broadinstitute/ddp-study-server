package org.broadinstitute.ddp.route;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.Auth0Constants;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dao.CenterUserDao;
import org.broadinstitute.ddp.db.dao.CenterProfileDao;
import org.broadinstitute.ddp.db.dto.CenterUserDto;
import org.broadinstitute.ddp.json.UserCreationPayload;
import org.broadinstitute.ddp.json.UserCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;

import com.auth0.jwt.JWT;

import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class UserCreationRoute extends ValidatedJsonInputRoute<UserCreationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, UserCreationPayload payload) throws Exception {
        var auth = RouteUtil.getDDPAuth(request);

        // The auth filters should be handling this, but just in case...
        if (auth == null) {
            var error = new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "no valid authorization found in request");
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, error);
        }

        // Don't worry about verifying the token here.
        // This should be considered an authenticated route, and the request
        // should be terminated well before this point if the token is not valid.
        final var token = JWT.decode(auth.getToken());
        final var domain = token.getIssuer();
        final var clientId = token.getClaim(Auth0Constants.DDP_CLIENT_CLAIM).asString();

        log.info("Attempt to create the user for study {}", payload.getStudyGuid());

        return TransactionWrapper.withTxn(handle -> {
            var internalCenterId = Long.valueOf(payload.getCenterId());
            final var centerDto = handle.attach(CenterProfileDao.class)
                .findById(internalCenterId)
                .orElseThrow(() -> {
                    var error = new ApiError(ErrorCodes.NOT_FOUND, "A center with id " + payload.getCenterId() + " could not be found");
                    return ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, error);
                });

            final var existingUserId = handle.attach(JdbiUser.class).getUserIdByEmail(payload.getEmail());
            if (existingUserId != null) {
                var error = new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS, "A user with the specified email address already exists.");
                /* 
                 * Either SC_CONFLICT (409) or SC_FORBIDDEN (403) would be applicable here.
                 * Going with 403 as it indicates:
                 *   - The server is refusing to process the request
                 *   - Authorization is not going to help
                 *   - The client should not repeat the request
                 */
                throw ResponseUtil.haltError(HttpStatus.SC_FORBIDDEN, error);
            }

            final var user = handle.attach(UserDao.class).createUserByEmail(payload.getEmail(), clientId, domain);

            var profile = UserProfile.builder()
                .userId(user.getId())
                .lastName(payload.getLastName())
                .firstName(payload.getFirstName())
                .birthDate(payload.getBirthDate())
                .build();

            var internalProfileId = handle.attach(UserProfileDao.class).createProfile(profile);
            if (internalProfileId == null) {
                var error = new ApiError(ErrorCodes.NOT_FOUND, "failed to create a new user profile");
                return ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, error);
            }

            handle.attach(CenterUserDao.class).insert(CenterUserDto.builder()
                            .centerId(centerDto.getId())
                            .userId(user.getId())
                            .build());

            return new UserCreationResponse(user, profile);
        });
    }
}

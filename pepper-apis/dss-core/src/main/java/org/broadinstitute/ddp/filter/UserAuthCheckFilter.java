package org.broadinstitute.ddp.filter;

import static spark.Spark.halt;

import java.util.ArrayList;
import java.util.List;

import com.auth0.jwt.exceptions.TokenExpiredException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.security.DDPAuth;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.route.HttpMethod;

/**
 * Checks route params and compares the requested user guid and requested study guid with the corresponding data encoded
 * in the {@link DDPAuth ddpAuth} parsed from the JWT token.  If the JWT's access matches the route params,  no action
 * is taken. If JWT does not grant access, the route is halted.
 */
@Slf4j
public class UserAuthCheckFilter implements Filter {
    private static final AuthPathRegexUtil pathMatcher = new AuthPathRegexUtil();

    private final List<AllowlistEntry> tempUserAllowlist = new ArrayList<>();

    /**
     * Add a route to the allowlist that enables temporary user access. If the route endpoint path has path parameters,
     * it should be using SparkJava's colon syntax (see {@link PathParam}). These path parameters will be converted to
     * regex in order to match the whole path to incoming requests.
     *
     * @param method   the http method name
     * @param endpoint the endpoint path, using path-param colon syntax as needed
     * @return this filter itself, for method chaining
     */
    public UserAuthCheckFilter addTempUserAllowlist(HttpMethod method, String endpoint) {
        String methodName = method.name().toUpperCase();
        String pathRegex = endpoint
                .replaceAll(PathParam.USER_GUID, ".+")
                .replaceAll(PathParam.STUDY_GUID, ".+")
                .replaceAll(PathParam.INSTANCE_GUID, ".+");
        tempUserAllowlist.add(new AllowlistEntry(methodName, pathRegex));
        return this;
    }

    @Override
    public void handle(Request request, Response response) {
        if (request.requestMethod().equals("OPTIONS")) {
            return;
        }

        DDPAuth ddpAuth = RouteUtil.getDDPAuth(request);
        if (ddpAuth.getToken() == null || ddpAuth.getOperator() == null) {
            handleTemporaryUser(request);
            return;
        }

        boolean canAccess = canAccess(request, ddpAuth);
        if (!canAccess) {
            log.warn("Retrying checking for access with non-cached permission data");
            try {
                canAccess = canAccess(request, RouteUtil.computeDDPAuth(request));
            } catch (TokenExpiredException e) {
                log.error("Found expired token for request", e);
                halt(401);
            } catch (Exception e) {
                log.error("Error while converting token for request", e);
                halt(401);
            }
        }

        if (!canAccess) {
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED,
                    new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "Authorization cannot be determined"));
        }
    }

    private boolean canAccess(Request request, DDPAuth ddpAuth) {
        String requestedUserGuid = request.params(PathParam.USER_GUID);
        String path = request.pathInfo();
        boolean canAccess = false;

        if (pathMatcher.isUserStudyRoute(path)) {
            // Need to do our own parsing since Spark does not parse out all params.
            String study = RouteUtil.parseStudyGuid(path);
            if (study == null) {
                throw new DDPException("Unable to parse study guid from request uri path: " + path);
            }
            if (pathMatcher.isGovernedStudyParticipantsRoute(path)) {
                canAccess = ddpAuth.canAccessGovernedUsers(requestedUserGuid);
            } else {
                canAccess = ddpAuth.canAccessStudyDataForUser(requestedUserGuid, study);
            }
        } else if (pathMatcher.isProfileRoute(path)) {
            canAccess = TransactionWrapper.withTxn(apiHandle ->
                    ddpAuth.canAccessUserProfile(apiHandle, requestedUserGuid));
        } else if (pathMatcher.isUpdateUserPasswordRoute(path) || pathMatcher.isUpdateUserEmailRoute(path)) {
            canAccess = ddpAuth.canUpdateLoginData(requestedUserGuid);
        } else if (pathMatcher.isGovernedParticipantsRoute(path)) {
            canAccess = ddpAuth.canAccessGovernedUsers(requestedUserGuid);
        } else if (pathMatcher.isAutocompleteRoute(path)
                || pathMatcher.isDrugSuggestionRoute(path)
                || pathMatcher.isCancerSuggestionRoute(path)
                || pathMatcher.isStudyStatisticsRoute(path)) {
            canAccess = ddpAuth.isActive();
        }
        return canAccess;
    }

    private void handleTemporaryUser(Request request) {
        String tempUserGuid = request.params(PathParam.USER_GUID);
        String requestedMethod = request.requestMethod();
        String requestedPath = request.pathInfo();

        log.info("Attempting to check temporary user with guid '{}' for request '{} {}'", tempUserGuid, requestedMethod, requestedPath);

        boolean inAllowlist = false;
        for (AllowlistEntry entry : tempUserAllowlist) {
            if (entry.allows(requestedMethod, requestedPath)) {
                inAllowlist = true;
                break;
            }
        }

        if (!inAllowlist) {
            log.warn("Request '{} {}' is not in temp-user allowlist", requestedMethod, requestedPath);
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED,
                    new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "Request is not in temp-user allowlist"));
        }

        UserDto tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).findByUserGuid(tempUserGuid));

        boolean canAccess = true;
        if (tempUser == null) {
            log.warn("Could not find temporary user with guid '{}'", tempUserGuid);
            canAccess = false;
        } else if (!tempUser.isTemporary()) {
            log.warn("User with guid '{}' is not a temporary user but is used to access"
                    + " temp-user allowlisted path without a token", tempUserGuid);
            canAccess = false;
        } else if (tempUser.isExpired()) {
            log.warn("Temporary user with guid '{}' had already expired at time {}ms", tempUserGuid, tempUser.getExpiresAtMillis());
            canAccess = false;
        }

        if (!canAccess) {
            ApiError apiError = new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "user is not authorized");
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, apiError);
        }
    }

    @AllArgsConstructor(access = AccessLevel.PACKAGE)
    private static class AllowlistEntry {
        private final String method;
        private final String pathRegex;

        public boolean allows(String requestedMethod, String requestedPath) {
            return requestedMethod.equalsIgnoreCase(method) && requestedPath.matches(pathRegex);
        }
    }
}

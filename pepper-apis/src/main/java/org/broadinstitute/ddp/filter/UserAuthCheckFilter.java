package org.broadinstitute.ddp.filter;

import java.util.ArrayList;
import java.util.List;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Filter;
import spark.Request;
import spark.Response;
import spark.route.HttpMethod;

/**
 * Checks route params and compares the requested user guid and requested study guid with the corresponding data encoded
 * in the {@link DDPAuth ddpAuth} parsed from the JWT token.  If the JWT's access matches the route params,  no action
 * is taken. If JWT does not grant access, the route is halted.
 */
public class UserAuthCheckFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(UserAuthCheckFilter.class);
    private static final AuthPathRegexUtil pathMatcher = new AuthPathRegexUtil();

    private final List<WhitelistEntry> tempUserWhitelist = new ArrayList<>();

    /**
     * Add a route to the whitelist that enables temporary user access. If the route endpoint path has path parameters,
     * it should be using SparkJava's colon syntax (see {@link PathParam}). These path parameters will be converted to
     * regex in order to match the whole path to incoming requests.
     *
     * @param method   the http method name
     * @param endpoint the endpoint path, using path-param colon syntax as needed
     * @return this filter itself, for method chaining
     */
    public UserAuthCheckFilter addTempUserWhitelist(HttpMethod method, String endpoint) {
        String methodName = method.name().toUpperCase();
        String pathRegex = endpoint
                .replaceAll(PathParam.USER_GUID, ".+")
                .replaceAll(PathParam.STUDY_GUID, ".+")
                .replaceAll(PathParam.INSTANCE_GUID, ".+");
        tempUserWhitelist.add(new WhitelistEntry(methodName, pathRegex));
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
                || pathMatcher.isDrugSuggestionRoute(path) || pathMatcher.isCancerSuggestionRoute(path)) {
            canAccess = ddpAuth.isActive();
        }

        if (!canAccess) {
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED,
                    new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "Authorization cannot be determined"));
        }
    }

    private void handleTemporaryUser(Request request) {
        String tempUserGuid = request.params(PathParam.USER_GUID);
        String requestedMethod = request.requestMethod();
        String requestedPath = request.pathInfo();

        LOG.info("Attempting to check temporary user with guid '{}' for request '{} {}'", tempUserGuid, requestedMethod, requestedPath);

        boolean inWhitelist = false;
        for (WhitelistEntry entry : tempUserWhitelist) {
            if (entry.allows(requestedMethod, requestedPath)) {
                inWhitelist = true;
                break;
            }
        }

        if (!inWhitelist) {
            LOG.warn("Request '{} {}' is not in temp-user whitelist", requestedMethod, requestedPath);
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED,
                    new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, "Request is not in temp-user whitelist"));
        }

        UserDto tempUser = TransactionWrapper.withTxn(handle ->
                handle.attach(JdbiUser.class).findByUserGuid(tempUserGuid));

        boolean canAccess = true;
        String message = null;
        if (tempUser == null) {
            LOG.warn("Could not find temporary user with guid '{}'", tempUserGuid);
            message = "Could not find temporary user with guid: " + tempUserGuid;
            canAccess = false;
        } else if (!tempUser.isTemporary()) {
            LOG.error("User with guid '{}' is not a temporary user but is used to access"
                    + " temp-user whitelisted path without a token", tempUserGuid);
            message = "User is not a temporary user";
            canAccess = false;
        } else if (tempUser.isExpired()) {
            LOG.warn("Temporary user with guid '{}' had already expired at time {}ms", tempUserGuid, tempUser.getExpiresAtMillis());
            message = "Temporary user passed expiration time";
            canAccess = false;
        }

        if (!canAccess) {
            ApiError apiError = new ApiError(ErrorCodes.AUTH_CANNOT_BE_DETERMINED, message);
            throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, apiError);
        }
    }

    private class WhitelistEntry {
        private final String method;
        private final String pathRegex;

        WhitelistEntry(String method, String pathRegex) {
            this.method = method;
            this.pathRegex = pathRegex;
        }

        public boolean allows(String requestedMethod, String requestedPath) {
            return requestedMethod.equalsIgnoreCase(method) && requestedPath.matches(pathRegex);
        }
    }
}

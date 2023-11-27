package org.broadinstitute.ddp.filter;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_BAD_REQUEST;
import static org.apache.http.HttpStatus.SC_UNAUTHORIZED;
import static org.broadinstitute.ddp.constants.ErrorCodes.INVALID_TOKEN;
import static org.broadinstitute.ddp.constants.ErrorCodes.REQUIRED_HEADER_MISSING;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.AUTHORIZATION;
import static org.broadinstitute.ddp.constants.RouteConstants.Header.BEARER;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import spark.Filter;
import spark.Request;
import spark.Response;

/**
 * A filter that checks auth0 log event token.<br>
 *
 * <p>Header ("Authorization") with authorization token can be (optionally) specified in
 * Auth0 Custom Webhook definition.<br>
 * If config parameter 'auth0LogEvents.token' is specified and not empty
 * then it contains a token which should be compared with token in header 'Authorization'
 * (in a format: 'Bearer ' + 'auth0LogEvents.token').
 * If token check is enabled and specified in config then the same token should be specified in
 * Auth0 Custom Webhook settings (with prefix 'Bearer ').
 */
@Slf4j
@AllArgsConstructor
public class Auth0LogEventCheckTokenFilter implements Filter {
    private final String cfgParamAuth0LogEventsToken;

    @Override
    public void handle(Request request, Response response) {
        checkAuthorizationToken(request);
    }

    private void checkAuthorizationToken(Request request) {
        if (isCheckToken()) {
            String authorizationToken = request.headers(AUTHORIZATION);
            if (authorizationToken == null) {
                haltError(SC_BAD_REQUEST, REQUIRED_HEADER_MISSING, "Header not specified: " + AUTHORIZATION);
            }
            if (!addBearerPrefixToToken(cfgParamAuth0LogEventsToken).equals(authorizationToken)) {
                haltError(SC_UNAUTHORIZED, INVALID_TOKEN, "Invalid authorization token");
            }
        }
    }

    private boolean isCheckToken() {
        return isNotBlank(cfgParamAuth0LogEventsToken);
    }

    private String addBearerPrefixToToken(String token) {
        return BEARER + token;
    }

    private void haltError(int status, String code, String msg) {
        log.warn(msg);
        throw ResponseUtil.haltError(status, new ApiError(code, msg));
    }
}

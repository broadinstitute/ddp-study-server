package org.broadinstitute.ddp.route;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.http.client.utils.URIBuilder;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.QueryParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dto.ClientDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * Auth0 callback route executed when the user's password is reset
 * Returns a redirect URL for a client
 */
public class PostPasswordResetRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(PostPasswordResetRoute.class);

    @Override
    public Object handle(Request request, Response response) throws Exception {
        String auth0ClientId = request.queryParams(QueryParam.AUTH0_CLIENT_ID);
        String auth0Domain = request.queryParams(QueryParam.AUTH0_DOMAIN);
        String email = request.queryParams(QueryParam.EMAIL);
        String auth0Success = request.queryParams(QueryParam.SUCCESS);

        if (StringUtils.isBlank(auth0ClientId)) {
            String errMsg = "auth0ClientId query string parameter is mandatory";
            LOG.warn(errMsg);
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.REQUIRED_PARAMETER_MISSING, errMsg));
        }

        URI clientPwdResetUrl = null;
        boolean isDomainSpecified = auth0Domain != null && !auth0Domain.isBlank();
        if (isDomainSpecified) {
            clientPwdResetUrl = getWebClientPasswordResetRedirectUrl(auth0ClientId, auth0Domain, response);
        } else {
            // Left for backward compatibility. It's expected that for some time
            // there will be no clashes between clients with the same Auth0 client id
            // When they start to occur, change the check accordingly
            LOG.info("Domain query parameter is missing, checking if the auth0 client id '{}' is unique", auth0ClientId);
            int numClients = TransactionWrapper.withTxn(
                    handle -> handle.attach(JdbiClient.class).countClientsWithSameAuth0ClientId(auth0ClientId)
            );
            if (numClients > 1) {
                String msg = String.format("Auth0 client id %s is not unique, please also provide domain query parameter", auth0ClientId);
                LOG.error(msg);
                throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
            } else if (numClients == 1) {
                LOG.info("All fine, client id '{}' is unique, nothing to worry about", auth0ClientId);
                clientPwdResetUrl = getWebClientPasswordResetRedirectUrl(auth0ClientId, null, response);
            }
        }

        var urlBuilder = new URIBuilder(clientPwdResetUrl);
        if (StringUtils.isNotBlank(email)) {
            urlBuilder.addParameter(QueryParam.EMAIL, email);
        }

        if (!Boolean.valueOf(auth0Success)) {
            String errMsg = "success parameter is FALSE, which means that the Auth0 link has expired";
            LOG.warn(errMsg);
            urlBuilder.addParameter(QueryParam.ERROR_CODE, ErrorCodes.PASSWORD_RESET_LINK_EXPIRED);
        }

        response.header(
                HttpHeaders.LOCATION,
                urlBuilder.build().toString()
        );
        response.status(HttpStatus.SC_MOVED_TEMPORARILY);
        return "";
    }

    private URI getWebClientPasswordResetRedirectUrl(String auth0ClientId, String auth0Domain, Response response) {
        return TransactionWrapper.withTxn(
                handle -> {
                    Optional<ClientDto> clientDtoOpt = null;
                    JdbiClient jdbiClient = handle.attach(JdbiClient.class);
                    if (auth0Domain != null) {
                        clientDtoOpt = jdbiClient.getClientByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
                    } else {
                        clientDtoOpt = jdbiClient.getClientByAuth0ClientId(auth0ClientId);
                    }
                    ClientDto clientDto = clientDtoOpt.orElseGet(
                            () -> {
                                String errMsg = "Client with Auth0 client id " + auth0ClientId + " does not exist";
                                LOG.warn(errMsg);
                                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                            }
                    );
                    boolean isRevoked = clientDto.isRevoked();
                    if (isRevoked) {
                        String errMsg = "The client is revoked";
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(
                                response,
                                HttpStatus.SC_UNPROCESSABLE_ENTITY,
                                new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, errMsg)
                        );
                    }
                    String redirUrl = clientDto.getWebPasswordRedirectUrl();
                    if (redirUrl == null) {
                        String errMsg = "Post-password redirect URL for the auth0 client with id " + auth0ClientId + " is not defined";
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(
                                response,
                                HttpStatus.SC_UNPROCESSABLE_ENTITY,
                                new ApiError(ErrorCodes.OPERATION_NOT_ALLOWED, errMsg)
                        );
                    }

                    URI parsedUrl = null;
                    try {
                        parsedUrl = new URL(redirUrl).toURI();
                    } catch (URISyntaxException | MalformedURLException e) {
                        String errMsg = "Post password reset URL " + redirUrl + " is malformed";
                        LOG.warn(errMsg);
                        throw ResponseUtil.haltError(
                                response,
                                HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                new ApiError(ErrorCodes.MALFORMED_REDIRECT_URL, errMsg)
                        );
                    }

                    return parsedUrl;
                }
        );
    }


}

package org.broadinstitute.ddp.route;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.auth0.json.mgmt.Connection;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.PasswordPolicy;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetStudyPasswordPolicyRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(GetStudyPasswordPolicyRoute.class);

    @Override
    public PasswordPolicy handle(Request request, Response response) {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        String clientId = request.queryParams(RouteConstants.QueryParam.AUTH0_CLIENT_ID);
        String domain = request.queryParams(RouteConstants.QueryParam.AUTH0_DOMAIN);

        LOG.info("Attempting to lookup password policy for study {}, client id {} and domain {}", studyGuid, clientId, domain);
        if (clientId == null || clientId.isBlank()) {
            LOG.warn("Client id is missing or blank");
            String msg = "Query parameter 'clientId' is required";
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        }

        PasswordPolicy policy = TransactionWrapper.withTxn(handle -> {
            if (domain == null || domain.isBlank()) {
                // Left for backward compatibility. It's expected that for some time
                // there will be no clashes between clients with the same Auth0 client id
                // When they start to occur, change the check accordingly
                LOG.info("Domain query parameter is missing, checking if the auth0 client id '{}' is unique", clientId);
                int numClients = handle.attach(JdbiClient.class).countClientsWithSameAuth0ClientId(clientId);
                if (numClients > 1) {
                    String msg = "Auth0 client id '{}' is not unique, please provide a domain value for disambiguation";
                    LOG.warn(msg);
                    throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
                } else if (numClients == 0) {
                    String msg = "Auth0 client id '{}' does not exist";
                    LOG.warn(msg);
                    throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, msg));
                }
                LOG.info("All fine, client id '{}' is unique, nothing to worry about", clientId);
            }
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            List<String> permittedStudies = handle.attach(JdbiClientUmbrellaStudy.class)
                    .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(clientId, domain);
            if (!permittedStudies.contains(studyGuid)) {
                LOG.warn("Either client does not exist or client does not have access to study " + studyGuid);
                String msg = "Could not find client with id " + clientId;
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            Auth0TenantDto tenantDto = handle.attach(JdbiAuth0Tenant.class)
                    .findById(studyDto.getAuth0TenantId())
                    .orElseThrow(() -> {
                        LOG.error("Could not find auth0 tenant for study with guid " + studyGuid);
                        String msg = "Error looking up password policy";
                        throw ResponseUtil.haltError(response, 500, new ApiError(ErrorCodes.SERVER_ERROR, msg));
                    });

            response.type(ContentType.APPLICATION_JSON.getMimeType());
            return lookupPasswordPolicy(tenantDto, clientId);
        });

        if (policy == null) {
            String msg = "Could not find password policy";
            throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, msg));
        } else {
            LOG.info("Found password policy for study {} and client id {} with policy type {}",
                    studyGuid, clientId, policy.getType());
            return policy;
        }
    }

    PasswordPolicy lookupPasswordPolicy(Auth0TenantDto tenantDto, String clientId) {
        var auth0Mgmt = createManagementClient(tenantDto);
        var listResult = auth0Mgmt.listClientConnections(clientId);

        if (listResult.hasThrown() || listResult.hasError()) {
            Exception e = listResult.hasThrown() ? listResult.getThrown() : listResult.getError();
            LOG.warn("Error getting client connections", e);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up password policy"));
        }

        List<Connection> dbConnections = listResult.getBody().stream()
                .filter(conn -> conn.getStrategy().equals(Auth0ManagementClient.DB_CONNECTION_STRATEGY))
                .collect(Collectors.toList());
        if (dbConnections.isEmpty()) {
            LOG.error("Password policies are only set on database connections but none were found for client {}", clientId);
            return null;
        } else if (dbConnections.size() > 1) {
            LOG.error("More than one database connection found for client {}, will attempt to use default one", clientId);
            // Attempt to put the default one in front, if there is one.
            dbConnections.sort((conn1, conn2) -> {
                if (conn1.getName().equals(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)) {
                    return -1;
                } else if (conn2.getName().equals(Auth0ManagementClient.DEFAULT_DB_CONN_NAME)) {
                    return 1;
                } else {
                    return 0;
                }
            });
        }

        Connection conn = dbConnections.get(0);
        String policyName = (String) conn.getOptions().get(Auth0ManagementClient.KEY_PASSWORD_POLICY);
        Map<String, Object> passwordOptions = (Map<String, Object>) conn.getOptions()
                .getOrDefault(Auth0ManagementClient.KEY_PASSWORD_COMPLEXITY_OPTIONS, new HashMap<>());
        Integer minLength = (Integer) passwordOptions.get(Auth0ManagementClient.KEY_MIN_LENGTH);

        if (minLength != null && minLength > PasswordPolicy.MAX_PASSWORD_LENGTH) {
            LOG.error("Password minimum length exceeds the maximum of {}", PasswordPolicy.MAX_PASSWORD_LENGTH);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up password policy"));
        }

        PasswordPolicy.PolicyType type;
        try {
            // Somehow Auth0 returns `null` when it's supposed to be `none`.
            type = (policyName == null ? PasswordPolicy.PolicyType.NONE : PasswordPolicy.PolicyType.valueOf(policyName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            LOG.error("Could not convert from password policy name '{}'", policyName);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up password policy"));
        }

        return PasswordPolicy.fromType(type, minLength);
    }

    Auth0ManagementClient createManagementClient(Auth0TenantDto tenantDto) {
        return new Auth0ManagementClient(
                tenantDto.getDomain(),
                tenantDto.getManagementClientId(),
                tenantDto.getManagementClientSecret());
    }
}

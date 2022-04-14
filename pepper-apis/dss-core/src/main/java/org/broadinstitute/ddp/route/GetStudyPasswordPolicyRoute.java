package org.broadinstitute.ddp.route;

import java.util.List;

import com.auth0.json.mgmt.Connection;
import org.apache.http.entity.ContentType;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.PasswordPolicy;
import org.broadinstitute.ddp.service.Auth0Service;
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

        LOG.info("Attempting to lookup password policy for study {} and client id {}", studyGuid, clientId);
        if (clientId == null || clientId.isBlank()) {
            LOG.warn("Client id is missing or blank");
            String msg = "Query parameter 'clientId' is required";
            throw ResponseUtil.haltError(response, 400, new ApiError(ErrorCodes.BAD_PAYLOAD, msg));
        }

        response.type(ContentType.APPLICATION_JSON.getMimeType());
        PasswordPolicy policy = TransactionWrapper.withTxn(handle -> {
            StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                String msg = "Could not find study with guid " + studyGuid;
                LOG.warn(msg);
                throw ResponseUtil.haltError(response, 404, new ApiError(ErrorCodes.NOT_FOUND, msg));
            }

            List<String> permittedStudies = handle.attach(JdbiClientUmbrellaStudy.class)
                    .findPermittedStudyGuidsByAuth0ClientIdAndAuth0TenantId(clientId, studyDto.getAuth0TenantId());

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
        var auth0Service = new Auth0Service(auth0Mgmt);

        Connection conn;
        try {
            conn = auth0Service.findClientDBConnection(clientId);
            if (conn == null) {
                return null; // No connection so no password policy.
            }
        } catch (Exception e) {
            LOG.warn("Error getting client connections", e);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up password policy"));
        }

        try {
            return auth0Service.extractPasswordPolicy(conn);
        } catch (Exception e) {
            LOG.error("Error extracting password policy from connection {}", conn.getName(), e);
            throw ResponseUtil.haltError(500, new ApiError(ErrorCodes.SERVER_ERROR, "Error looking up password policy"));
        }
    }

    Auth0ManagementClient createManagementClient(Auth0TenantDto tenantDto) {
        return new Auth0ManagementClient(
                tenantDto.getDomain(),
                tenantDto.getManagementClientId(),
                tenantDto.getManagementClientSecret());
    }
}

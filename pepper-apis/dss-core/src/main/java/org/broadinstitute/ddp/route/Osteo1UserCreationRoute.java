package org.broadinstitute.ddp.route;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.client.Auth0ManagementClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ClientDao;
import org.broadinstitute.ddp.db.dao.JdbiAuth0Tenant;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.Auth0TenantDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.users.requests.UserCreationPayload;
import org.broadinstitute.ddp.security.StudyClientConfiguration;
import org.broadinstitute.ddp.service.participants.Osteo1ParticipantCreator;
import org.broadinstitute.ddp.util.Auth0Util;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

import static spark.Spark.halt;

/**
 * Creates & enrolls an Osteo#1 user
 * Create user, auth0 user, create and fills prequal, consent & release v1 activity instances
 * Depending on birthDate passed, current age is calculated and self, pediatric (<7 or >=/7) activities are created
 */
@Slf4j
public class Osteo1UserCreationRoute extends ValidatedJsonInputRoute<UserCreationPayload> {
    private final TaskPublisher taskPublisher;
    private String auth0ClientId = null;

    public Osteo1UserCreationRoute(TaskPublisher taskPublisher) {
        this.taskPublisher = taskPublisher;
    }

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_UNPROCESSABLE_ENTITY;
    }

    @Override
    public Object handle(Request request, Response response, UserCreationPayload payload) throws Exception {
        String studyGuid = request.params(RouteConstants.PathParam.STUDY_GUID);
        payload.setStudyGuid(studyGuid);
        Config cfg = ConfigManager.getInstance().getConfig();
        Config auth0Config = cfg.getConfig(ConfigFile.AUTH0);
        if (!cfg.hasPath(ConfigFile.ALLOW_OS1_USER_CREATION) || !cfg.getBoolean(ConfigFile.ALLOW_OS1_USER_CREATION)) {
            throw ResponseUtil.haltError(HttpStatus.SC_FORBIDDEN, "OS1 User Creation not permitted");
        }
        if (!auth0Config.hasPath(ConfigFile.OSTEO_AUTH0_CLIENT_ID) || auth0Config.getString(ConfigFile.OSTEO_AUTH0_CLIENT_ID).isEmpty()) {
            throw ResponseUtil.haltError(HttpStatus.SC_FORBIDDEN, "ClientId not found for " + studyGuid);
        } else {
            auth0ClientId = auth0Config.getString(ConfigFile.OSTEO_AUTH0_CLIENT_ID);
        }

        return TransactionWrapper.withTxn(handle -> {

            StudyDto study = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);

            //do Auth checks
            //get domain
            String auth0Domain = handle.attach(JdbiAuth0Tenant.class).findById(study.getAuth0TenantId())
                    .map(Auth0TenantDto::getDomain)
                    .orElse(null);
            log.info("Using auth0 domain {} for user registration", auth0Domain);

            StudyClientConfiguration clientConfig = handle.attach(ClientDao.class).getConfiguration(auth0ClientId, auth0Domain);
            if (clientConfig == null) {
                log.warn("Attempted to register new user using Auth0 client {} that is revoked or not found", auth0ClientId);
                throw halt(HttpStatus.SC_UNAUTHORIZED);
            }

            Auth0ManagementClient mgmtClient = Auth0Util.getManagementClientForDomain(handle, auth0Domain);
            final var internalClientId = handle.attach(JdbiClient.class)
                    .getClientIdByAuth0ClientAndDomain(auth0ClientId, auth0Domain);
            if (internalClientId.isEmpty()) {
                var error = new ApiError(ErrorCodes.NOT_FOUND,
                        String.format("Auth0 client '%s' is not authorized for '%s'.",
                                auth0ClientId, auth0Domain));
                throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, error);
            }

            Osteo1ParticipantCreator osteo1ParticipantCreator = new Osteo1ParticipantCreator(taskPublisher, mgmtClient, auth0ClientId);
            return osteo1ParticipantCreator.createOsteo1User(handle, request, response, payload);
        });
    }

}

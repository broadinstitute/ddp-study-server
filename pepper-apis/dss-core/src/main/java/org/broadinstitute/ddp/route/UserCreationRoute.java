package org.broadinstitute.ddp.route;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.json.users.models.EmailAddress;
import org.broadinstitute.ddp.json.users.models.Guid;
import org.broadinstitute.ddp.json.users.requests.UserCreationPayload;
import org.broadinstitute.ddp.json.users.responses.UserCreationResponse;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.service.participants.ParticipantsCreateService;
import org.broadinstitute.ddp.service.participants.ParticipantsServiceError;
import org.broadinstitute.ddp.service.studies.StudiesServiceError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

@Slf4j
@AllArgsConstructor
public class UserCreationRoute extends ValidatedJsonInputRoute<UserCreationPayload> {
    private final TaskPublisher taskPublisher;
    
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_UNPROCESSABLE_ENTITY;
    }

    @Override
    public Object handle(Request request, Response response, UserCreationPayload payload) throws Exception {
        var auth = RouteUtil.getDDPAuth(request);
        /*
        * Grab the requesting client id out of the token
        * so it can be referred back to after we create the user
        */ 
        final var requestorClientId = auth.getClient();
        final var domain = auth.getDomain();
        if (payload.getStudyGuid() == null) {
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, "Invalid StudyGuid");
        }
        final var studyGuid = new Guid(payload.getStudyGuid());
        final EmailAddress email;
        
        try {
            email = new EmailAddress(payload.getEmail());
        } catch (IllegalArgumentException exception) {
            var invalidEmail = new ApiError(ErrorCodes.BAD_PAYLOAD, "The email address is missing or malformed.");
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, invalidEmail);
        }

        log.info("attempting to create a user for study {}", payload.getStudyGuid());

        return TransactionWrapper.withTxn(handle -> {
            // If we've made it this far in the request, it's unlikely the Auth0 client is not
            // going to be recognized but handle things just in case. Save the ID since it'll be
            // needed below to set the `created_by_client_id` field in the user.
            final var internalClientId = handle.attach(JdbiClient.class)
                    .getClientIdByAuth0ClientAndDomain(requestorClientId, domain);
            if (internalClientId.isEmpty()) {
                var error = new ApiError(ErrorCodes.NOT_FOUND,
                        String.format("Auth0 client '%s' is not authorized for '%s'.",
                            requestorClientId, domain));
                throw ResponseUtil.haltError(HttpStatus.SC_UNAUTHORIZED, error);
            }

            final var participantService = new ParticipantsCreateService(handle);
            
            User newUser = null;
            try {
                newUser = participantService.createWithEmail(email, studyGuid);
            } catch (ParticipantsServiceError error) {
                switch (error.getCode()) {
                    case USER_EXISTS:
                        var userExists = new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS,
                                "A participant with the specified email address already exists.");
                        throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, userExists);
                    default:
                        var message = "an error occurred while creating a participant";
                        log.error(message, error);
                        var serverError = new ApiError(ErrorCodes.SERVER_ERROR, message);
                        throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, serverError);
                }
            } catch (StudiesServiceError error) {
                switch (error.getCode()) {
                    case PARTICIPANT_ALREADY_REGISTERED:
                        var alreadyRegistered = new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS,
                                    String.format("The email address has already been registered to a user"));
                        throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, alreadyRegistered);
                    case STUDY_NOT_FOUND:
                        var studyNotFound = new ApiError(ErrorCodes.STUDY_NOT_FOUND,
                                    String.format("The study '%s' could not be found.", studyGuid));
                        throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, studyNotFound);
                    default:
                        var message = "an error occurred while creating a participant";
                        log.error(message, error);
                        var serverError = new ApiError(ErrorCodes.SERVER_ERROR, message);
                        throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, serverError);
                }
            }

            // Update the "created-by" field after the fact since that's a property specific
            // to the request.
            var result = handle.attach(UserDao.class)
                    .getUserSql()
                    .updateUser(newUser.getId(),
                        internalClientId.orElse(null),
                        newUser.getAuth0TenantId().orElse(null),
                        newUser.getAuth0UserId().orElse(null),
                        newUser.isLocked(),
                        Instant.now().toEpochMilli(),
                        newUser.getExpiresAt());
            if (result != 1) {
                var createdByError = new ApiError(ErrorCodes.SERVER_ERROR,
                        "failed to update the participant's 'created-by' client.");
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, createdByError);
            }

            var profile = UserProfile.builder()
                    .userId(newUser.getId())
                    .lastName(payload.getLastName())
                    .firstName(payload.getFirstName())
                    .birthDate(payload.getBirthDate())
                    .build();

            var internalProfileId = handle.attach(UserProfileDao.class).createProfile(profile);
            if (internalProfileId == null) {
                var error = new ApiError(ErrorCodes.NOT_FOUND, "failed to create a new user profile");
                return ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, error);
            }

            // Necessary for Housekeeping to perform a data sync operation
            // to get the new user into ES
            taskPublisher.publishTask(
                    TaskPubSubPublisher.TASK_PARTICIPANT_REGISTERED,
                    StringUtils.EMPTY, // No payload necessary here
                    studyGuid.getValue(),
                    newUser.getGuid());

            // Suggested by @ssettipalli
            // The new user doesn't seems to appear in ElasticSearch without this
            // line- look into what's going on with the export.
            handle.attach(DataExportDao.class).queueDataSync(newUser.getGuid());

            return new UserCreationResponse(newUser, profile);
        });
    }
}

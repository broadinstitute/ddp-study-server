package org.broadinstitute.ddp.route;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiClient;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.event.publish.TaskPublisher;
import org.broadinstitute.ddp.event.publish.pubsub.TaskPubSubPublisher;
import org.broadinstitute.ddp.json.UserCreationPayload;
import org.broadinstitute.ddp.json.UserCreationResponse;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.service.participants.ParticipantsCreateService;
import org.broadinstitute.ddp.service.participants.ParticipantsCreateService.ParticipantCreateError;
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
        final var studyGuid = payload.getStudyGuid();

        final var email = payload.getEmail();
        final var emailValidator = EmailValidator.getInstance();

        /* 
        * It's not absolutely necessary to check here- the ParticipantsCreateService will also
        *  check that the email is correctly formatted (you'd need to update its associated
        *  switch statement below in the transaction)
        *
        * Checking here is in order to save a few cycles & connections to the DB if it's
        *  immediately clear the client gave us bad data.
        */
        if (emailValidator.isValid(email) == false) {
            var error = new ApiError(ErrorCodes.MALFORMED_EMAIL, "the email address is not in a valid format.");
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, error);
        }

        if (StringUtils.isNotBlank(payload.getCenterId())) {
            var error = new ApiError(ErrorCodes.NOT_SUPPORTED,
                    "passing a non-null center id when creating a participant is not supported yet.");
            throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, error);
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
            } catch (ParticipantCreateError cause) {
                switch (cause.getCode()) {
                    case USER_EXISTS:
                        var userExists = new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS,
                                "A participant with the specified email address already exists.");
                        throw ResponseUtil.haltError(HttpStatus.SC_UNPROCESSABLE_ENTITY, userExists);
                    case STUDY_DOES_NOT_EXIST:
                        var studyNotFound = new ApiError(ErrorCodes.STUDY_NOT_FOUND,
                                    String.format("The study '%s' could not be found.", studyGuid));
                        throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, studyNotFound);
                    default:
                        var message = "an error occurred while creating a participant";
                        log.error(message, cause);
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
                var error = new ApiError(ErrorCodes.EMAIL_ALREADY_EXISTS, "failed to update the participant's 'created-by' client.");
                throw ResponseUtil.haltError(HttpStatus.SC_INTERNAL_SERVER_ERROR, error);
            }

            // Creating a profile requires a non-null user id, so a profile can't be created
            // before a user has already been created.
            // Easing this restriction may give us some additional flexibility when performing
            // validations.
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
                    studyGuid,
                    newUser.getGuid());

            // Suggested by @ssettipalli
            handle.attach(DataExportDao.class).queueDataSync(newUser.getGuid());

            return new UserCreationResponse(newUser, profile);
        });
    }
}

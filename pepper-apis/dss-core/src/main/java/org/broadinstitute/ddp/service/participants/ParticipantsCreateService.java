package org.broadinstitute.ddp.service.participants;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.broadinstitute.ddp.db.dao.CenterProfileDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.participants.ParticipantsCreateService.ParticipantCreateError.Code;
import org.broadinstitute.ddp.service.studies.StudiesService;
import org.broadinstitute.ddp.service.studies.StudiesService.StudiesServiceError;
import org.jdbi.v3.core.Handle;

@Slf4j
public class ParticipantsCreateService {

    @Value
    public static class ParticipantCreateError extends Exception {
        public enum Code {
            USER_EXISTS,
            INSUFFICIENT_PERMISSION,
            CENTER_DOES_NOT_EXIST,
            STUDY_DOES_NOT_EXIST,
            STUDY_REGISTRATION_FAILED,

            /**
             * The email address provided was not formatted correctly, and did not validate.
            */
            MALFORMED_EMAIL,

            /**
             * An unhandled error occurred in one of the service's dependencies. Details of the
             * error are included, see {@link java.lang.Exception#getCause()}
             */
            INTERNAL_ERROR,

            /**
             * The functionality needed for this operation is not yet implemented
             */
            NOT_IMPLEMENTED
        }

        private Code code;

        public ParticipantCreateError(Code code) {
            super();
            this.code = code;
        }

        public ParticipantCreateError(Code code, String message) {
            super(message);
            this.code = code;
        }

        public ParticipantCreateError(Code code, Throwable cause) {
            super(code.toString(), cause);
            this.code = code;
        }

        public ParticipantCreateError(Code code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    @NonNull
    private Handle handle;

    public ParticipantsCreateService(@NonNull Handle handle) {
        this.handle = handle;
    }

    public User createWithEmail(String email, String studyGuid) throws ParticipantCreateError {
        return this.createWithEmail(email, studyGuid, null);
    }

    /**
     * Creates a new participant and optionally registers them into a study
     * and/or a center.
     * 
    * <p>Email validation is handled by {@link org.apache.commons.validator.routines.EmailValidator#isValid(String)}
    * @see <a href="https://github.com/apache/commons-validator/blob/42a07afd5144105483d18f87e07f5e5dcb6802e8/src/main/java/org/apache/commons/validator/routines/EmailValidator.java#L40-L51">apache/commons-validator EmailValidator.java</a>
     * @param email the email for the new participant.
     * @param studyGuid the study to register the participant into. may be null.
     * @param centerGuid the center to register the participant with. may be null.
     * @return the created participant
     * @throws ParticipantCreateError if the participant could not be created, or already exists
     */
    public User createWithEmail(@NonNull String email, String studyGuid, String centerGuid) throws ParticipantCreateError {

        var emailValidator = EmailValidator.getInstance();
        if (emailValidator.isValid(email)) {
            throw new ParticipantCreateError(Code.MALFORMED_EMAIL);
        }

        Long centerId = null;

        if (StringUtils.isNotBlank(centerGuid)) {
            /* The "centerGuid" is currently just the internal ID
             * which is a `long` type so convert this to a long before
             * checking the database. If/when Centers get an separate external
             * identifier (such as a GUID), then this will need to be updated.
             */
            Long tempCenterGuid = Long.valueOf(centerGuid);
            if (tempCenterGuid != null) {
                var centerDto = handle.attach(CenterProfileDao.class)
                        .findById(tempCenterGuid)
                        .orElseThrow(() -> {
                            return new ParticipantCreateError(Code.CENTER_DOES_NOT_EXIST,
                                "The center does not exist.");
                        });
                centerId = centerDto.getId();
            }
        }

        if (centerId != null) {
            throw new ParticipantCreateError(Code.NOT_IMPLEMENTED, 
                "Associating participants with a center is not implemented");
        }

        var newUser = handle.attach(UserDao.class)
                .createUserByEmail(email);

        var studyService = new StudiesService(handle);

        // Going to be using this in a couple of places, so set it aside
        final var newUserGuid = newUser.getGuid();
        try {
            // Cross our fingers and hope the registration succeeds!
            studyService.registerParticipantInStudy(newUserGuid, studyGuid);
        } catch (StudiesServiceError sse) {
            var message = String.format("failed to register user %s for study %s", newUserGuid, studyGuid);
            throw new ParticipantCreateError(Code.STUDY_REGISTRATION_FAILED, message, sse);
        }

        return newUser;
    }
 
    public User createWithAuth0(String userId, String tenantDomain) throws ParticipantCreateError {
        throw new ParticipantCreateError(Code.NOT_IMPLEMENTED,
                "Auth0 participant account creation not implemented");
    }
}

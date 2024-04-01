package org.broadinstitute.ddp.service.participants;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.json.users.models.EmailAddress;
import org.broadinstitute.ddp.json.users.models.Guid;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventSignal;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.service.studies.StudiesService;
import org.broadinstitute.ddp.service.studies.StudiesServiceError;
import org.jdbi.v3.core.Handle;

@Slf4j
public class ParticipantsCreateService {

    @NonNull
    private final Handle handle;


    public ParticipantsCreateService(@NonNull Handle handle) {
        this.handle = handle;
    }

    public User createWithEmail(EmailAddress email, Guid studyGuid) throws ParticipantsServiceError, StudiesServiceError {
        return createWithEmail(email.getValue(), studyGuid.getValue());
    }

    /**
     * Creates a new participant and optionally registers them into a study
     * and/or a center.
     * 
    * <p>Email validation is handled by {@link org.apache.commons.validator.routines.EmailValidator#isValid(String)}
    * @see <a href="https://github.com/apache/commons-validator/blob/42a07afd5144105483d18f87e07f5e5dcb6802e8/src/main/java/org/apache/commons/validator/routines/EmailValidator.java#L40-L51">apache/commons-validator EmailValidator.java</a>
     * @param email the email for the new participant.
     * @param studyGuid the study to register the participant into. may be null.
     * @return the created participant
     * @throws ParticipantsServiceError if an error occurs during user creation
     * @throws StudiesServiceError if an error occurs during user registration
     */
    public User createWithEmail(@NonNull String email, @NonNull String studyGuid)
        throws ParticipantsServiceError, StudiesServiceError {

        var studyService = new StudiesService(handle);

        /* 
         * This check could use another set of eyes.
         * It may be better to optimistically create a new user, then roll
         * things back if the study ends up not existing (optimize the happy
         * path)
        */
        if (studyService.studyExists(studyGuid) == false) {
            var message = String.format("study %s could not be found", studyGuid);
            throw new StudiesServiceError(StudiesServiceError.Code.STUDY_NOT_FOUND, message);
        }

        var newUser = handle.attach(UserDao.class).createUserByEmail(email);
        if (newUser == null) {
            throw new ParticipantsServiceError(ParticipantsServiceError.Code.USER_EXISTS,
                    "failed to create a new user with the specified email");
        }

        // Going to be using this in a couple of places, so set it aside
        final var newUserGuid = newUser.getGuid();
        try {
            // Cross our fingers and hope the registration succeeds!
            studyService.registerParticipantInStudy(newUserGuid, studyGuid);
        } catch (StudiesServiceError sse) {
            var message = String.format("failed to register user %s for study %s", newUserGuid, studyGuid);
            throw new ParticipantsServiceError(ParticipantsServiceError.Code.STUDY_REGISTRATION_FAILED, message, sse);
        }

        // Assumes the operator and the user are one in the same in this case.
        // Need to check this assumption, however.
        notifyParticipantWasRegistered(studyGuid, newUserGuid, newUserGuid);

        return newUser;
    }

    private void notifyParticipantWasRegistered(String studyGuid, String operatorGuid, String participantGuid) {
        var studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
        var userDao = handle.attach(JdbiUser.class);
        var operator = userDao.findByUserGuid(operatorGuid);
        var participant = userDao.findByUserGuid(participantGuid);

        var signal = new EventSignal(
                operator.getUserId(),
                participant.getUserId(),
                participant.getUserGuid(),
                operator.getUserGuid(),
                studyDto.getId(),
                studyDto.getGuid(),
                EventTriggerType.USER_REGISTERED);
        EventService.getInstance().processAllActionsForEventSignal(handle, signal);
    }
}

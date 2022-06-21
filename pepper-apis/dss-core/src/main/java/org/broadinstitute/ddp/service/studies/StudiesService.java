package org.broadinstitute.ddp.service.studies;

import java.util.Optional;

import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.studies.StudiesService.StudiesServiceError.Code;
import org.jdbi.v3.core.Handle;

@Slf4j
public class StudiesService {

    @Value
    public static class StudiesServiceError extends Exception {
        public enum Code {
            STUDY_NOT_FOUND,
            PARTICIPANT_ALREADY_REGISTERED,
            DATABASE_ERROR,
            NOT_IMPLEMENTED
        }

        private Code code;

        public StudiesServiceError(Code code) {
            // Works for now to expose the code in a usable way to
            // the superclasses, but not too thrilled about it.
            //
            // Open to suggestions.
            super(code.toString());
            this.code = code;
        }

        public StudiesServiceError(Code code, String message) {
            super(message);
            this.code = code;
        }

        public StudiesServiceError(Code code, Throwable cause) {
            super(code.toString(), cause);
            this.code = code;
        }

        public StudiesServiceError(Code code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    @NonNull
    private Handle handle;

    /**
     * A request-lived object to manage performing actions on a study.
     * 
     * @param handle The JDBI handle to use when performing queries.
     */
    public StudiesService(@NonNull Handle handle) {
        this.handle = handle;
    }

    // Needs a terminology check- is `register` the correct word for adding a participant to
    // the study?

    /**
     * Registers a participant with a study.
     * 
     * <P>This method will either successfully register a user into the study,
     * or throw an exception. Runtime exceptions are not handled.
     * 
     * @throws StudiesServiceError with details about the error.
     */
    public void registerParticipantInStudy(@NonNull String participantGuid, @NonNull String studyGuid) throws StudiesServiceError {
        if (studyExists(studyGuid) == false) {
            throw new StudiesServiceError(Code.STUDY_NOT_FOUND);
        }

        if (isParticipantRegisteredInStudy(participantGuid, studyGuid)) {
            throw new StudiesServiceError(Code.PARTICIPANT_ALREADY_REGISTERED);
        }

        // Skipping a null-check on the result intentionally.
        // If the attach() is failing, things have already gone horribly wrong,
        // and an NPE would be a saving grace.
        //
        // Side note: consider doing the `attach()` in the constructor. Preload/lazy-load the common 
        //  jdbi objects in private ivars to prevent having to recreate this for each call?
        var enrollmentJdbi = handle.attach(JdbiUserStudyEnrollment.class);

        // The starting status may be important information that shouldn't belong here.
        //  Consider moving this to a static public method in the EnrollmentStatusType?
        final var startingStatus = EnrollmentStatusType.REGISTERED;

        // The expectation here is that this will either throw, or succeed.
        // We don't actually need the return value.
        // 
        // Any suggestions?
        enrollmentJdbi.changeUserStudyEnrollmentStatus(participantGuid, studyGuid, startingStatus);

        // Done!
    }

    public boolean isParticipantRegisteredInStudy(@NonNull String participantGuid, @NonNull String studyGuid) {
        var enrollmentJdbi = handle.attach(JdbiUserStudyEnrollment.class);
        var result = enrollmentJdbi.findIdByUserAndStudyGuid(participantGuid, studyGuid);
        if (result.isPresent()) {
            // If any id has been returned, the participant is registered
            // Let the caller know.
            return true;
        } else {
            return false;
        }
    }

    private boolean studyExists(String studyGuid) {
        var studyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(studyGuid);
        if (studyId.isPresent()) {
            // If a study with the passed guid was found, return true
            return true;
        } else {
            return false;
        }
    }
}

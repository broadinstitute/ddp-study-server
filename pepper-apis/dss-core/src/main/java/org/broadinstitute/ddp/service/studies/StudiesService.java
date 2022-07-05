package org.broadinstitute.ddp.service.studies;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.Handle;

@Slf4j
public class StudiesService {

    @NonNull
    private Handle handle;

    private JdbiUserStudyEnrollment studyEnrollmentDao;
    private JdbiUmbrellaStudy studyDao;

    /**
     * A request-lived object to manage performing actions on a study.
     * 
     * @param handle The JDBI handle to use when performing queries.
     */
    public StudiesService(@NonNull Handle handle) {
        this.handle = handle;
    }

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
            throw new StudiesServiceError(StudiesServiceError.Code.STUDY_NOT_FOUND);
        }

        if (isParticipantRegisteredInStudy(participantGuid, studyGuid)) {
            throw new StudiesServiceError(StudiesServiceError.Code.PARTICIPANT_ALREADY_REGISTERED);
        }

        // The starting status may be important information that shouldn't belong here.
        //  Consider moving this to a static public method in the EnrollmentStatusType?
        final var startingStatus = EnrollmentStatusType.REGISTERED;

        // The expectation here is that this will either throw, or succeed.
        // We don't actually need the return value.
        getUserStudyEnrollmentDao().changeUserStudyEnrollmentStatus(participantGuid, studyGuid, startingStatus);
    }

    public boolean isParticipantRegisteredInStudy(@NonNull String participantGuid, @NonNull String studyGuid) {
        /*
        *  `REGISTERED` is the required first state for any user registered into a study,
        *   and all subsquent states imply that the user is registered. As a result,
        *   just checking for `isPresent()` here is fine, since _any_ state implies the
        *   participant is registered.
        */
        return getUserStudyEnrollmentDao()
            .findIdByUserAndStudyGuid(participantGuid, studyGuid)
            .isPresent();
    }

    public boolean studyExists(String studyGuid) {
        var studyId = getUmbrellaStudyDao().getIdByGuid(studyGuid);
        return studyId.isPresent();
    }

    private JdbiUmbrellaStudy getUmbrellaStudyDao() {
        if (studyDao == null) {
            studyDao = new JdbiUmbrellaStudyCached(handle);
        }

        return studyDao;
    }

    private JdbiUserStudyEnrollment getUserStudyEnrollmentDao() {
        if (studyEnrollmentDao == null) {
            studyEnrollmentDao = handle.attach(JdbiUserStudyEnrollment.class);
        }

        return studyEnrollmentDao;
    }
}

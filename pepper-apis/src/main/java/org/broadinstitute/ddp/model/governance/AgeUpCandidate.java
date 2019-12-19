package org.broadinstitute.ddp.model.governance;

import java.time.LocalDate;

import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents a study participant that should be checked for the age-up process.
 */
public class AgeUpCandidate {

    private long id;
    private long studyId;
    private String studyGuid;
    private long participantUserId;
    private String participantUserGuid;
    private EnrollmentStatusType status;
    private LocalDate birthDate;
    private boolean hasInitiatedPrep;

    @JdbiConstructor
    public AgeUpCandidate(@ColumnName("age_up_candidate_id") long id,
                          @ColumnName("study_id") long studyId,
                          @ColumnName("study_guid") String studyGuid,
                          @ColumnName("participant_user_id") long participantUserId,
                          @ColumnName("participant_user_guid") String participantUserGuid,
                          @ColumnName("enrollment_status") EnrollmentStatusType status,
                          @ColumnName("birth_date") LocalDate birthDate,
                          @ColumnName("initiated_preparation") boolean hasInitiatedPrep) {
        this.id = id;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.participantUserId = participantUserId;
        this.participantUserGuid = participantUserGuid;
        this.status = status;
        this.birthDate = birthDate;
        this.hasInitiatedPrep = hasInitiatedPrep;
    }

    public long getId() {
        return id;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }

    public long getParticipantUserId() {
        return participantUserId;
    }

    public String getParticipantUserGuid() {
        return participantUserGuid;
    }

    public EnrollmentStatusType getStatus() {
        return status;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public boolean hasInitiatedPrep() {
        return hasInitiatedPrep;
    }
}

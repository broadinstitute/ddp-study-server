package org.broadinstitute.ddp.model.governance;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents a study participant that should be checked for the age-up process.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class AgeUpCandidate {
    @ColumnName("age_up_candidate_id")
    long id;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_guid")
    String studyGuid;

    @ColumnName("participant_user_id")
    long participantUserId;

    @ColumnName("operator_user_id")
    long operatorUserId;

    @ColumnName("participant_user_guid")
    String participantUserGuid;

    @ColumnName("operator_user_guid")
    String operatorUserGuid;

    @ColumnName("enrollment_status")
    EnrollmentStatusType status;

    @ColumnName("birth_date")
    LocalDate birthDate;

    @Accessors(fluent = true)
    @ColumnName("initiated_preparation")
    boolean hasInitiatedPrep;
}

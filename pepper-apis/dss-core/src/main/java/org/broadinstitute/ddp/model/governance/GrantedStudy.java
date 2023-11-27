package org.broadinstitute.ddp.model.governance;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * A study that a proxy can access for a governed user.
 */
@Value
@AllArgsConstructor(onConstructor = @__(@JdbiConstructor))
public class GrantedStudy {
    @ColumnName("user_study_governance_id")
    long studyGovernanceId;

    @ColumnName("user_governance_id")
    long governanceId;

    @ColumnName("study_id")
    long studyId;

    @ColumnName("study_guid")
    String studyGuid;
}

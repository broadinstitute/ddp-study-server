package org.broadinstitute.ddp.model.governance;

import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * A study that a proxy can access for a governed user.
 */
public class GrantedStudy {

    private long studyGovernanceId;
    private long governanceId;
    private long studyId;
    private String studyGuid;

    @JdbiConstructor
    public GrantedStudy(@ColumnName("user_study_governance_id") long studyGovernanceId,
                        @ColumnName("user_governance_id") long governanceId,
                        @ColumnName("study_id") long studyId,
                        @ColumnName("study_guid") String studyGuid) {
        this.studyGovernanceId = studyGovernanceId;
        this.governanceId = governanceId;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
    }

    public long getStudyGovernanceId() {
        return studyGovernanceId;
    }

    public long getGovernanceId() {
        return governanceId;
    }

    public long getStudyId() {
        return studyId;
    }

    public String getStudyGuid() {
        return studyGuid;
    }
}

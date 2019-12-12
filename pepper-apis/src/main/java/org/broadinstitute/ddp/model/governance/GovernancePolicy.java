package org.broadinstitute.ddp.model.governance;

import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;

/**
 * Represents the governance policy for a study.
 */
public class GovernancePolicy {

    private long id;
    private long studyId;
    private String studyGuid;
    private Expression shouldCreateGovernedUserExpr;

    @JdbiConstructor
    public GovernancePolicy(@ColumnName("study_governance_policy_id") long id,
                            @ColumnName("study_id") long studyId,
                            @ColumnName("study_guid") String studyGuid,
                            @Nested("scgu") Expression shouldCreateGovernedUserExpr) {
        this.id = id;
        this.studyId = studyId;
        this.studyGuid = studyGuid;
        this.shouldCreateGovernedUserExpr = shouldCreateGovernedUserExpr;
    }

    public GovernancePolicy(long studyId, Expression shouldCreateGovernedUserExpr) {
        this.studyId = studyId;
        this.shouldCreateGovernedUserExpr = shouldCreateGovernedUserExpr;
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

    public Expression getShouldCreateGovernedUserExpr() {
        return shouldCreateGovernedUserExpr;
    }
}

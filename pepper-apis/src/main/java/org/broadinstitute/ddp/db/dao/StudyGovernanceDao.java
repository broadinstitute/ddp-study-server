package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
import org.broadinstitute.ddp.model.governance.AgeUpCandidate;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface StudyGovernanceDao extends SqlObject {

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    StudyGovernanceSql getStudyGovernanceSql();

    default GovernancePolicy createPolicy(GovernancePolicy policy) {
        StudyGovernanceSql studyGovernanceSql = getStudyGovernanceSql();

        Expression scguExpr = getJdbiExpression().insertExpression(policy.getShouldCreateGovernedUserExpr().getText());
        long policyId = studyGovernanceSql.insertPolicy(true, policy.getStudyId(), null, scguExpr.getId());

        int order = 1;
        for (AgeOfMajorityRule rule : policy.getAgeOfMajorityRules()) {
            studyGovernanceSql.insertAgeOfMajorityRule(policyId, rule.getCondition(), rule.getAge(), rule.getPrepMonths(), order);
            order += 1;
        }

        return findPolicyById(policyId).orElseThrow(() -> new DaoException("Could not find study governance policy with id " + policyId));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllPolicies")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    @RegisterConstructorMapper(value = AgeOfMajorityRule.class, prefix = "aom")
    @UseRowReducer(PolicyWithAgeOfMajorityRuleReducer.class)
    Stream<GovernancePolicy> findAllPolicies();

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyById")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    @RegisterConstructorMapper(value = AgeOfMajorityRule.class, prefix = "aom")
    @UseRowReducer(PolicyWithAgeOfMajorityRuleReducer.class)
    Optional<GovernancePolicy> findPolicyById(@Bind("id") long policyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyByStudyId")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    @RegisterConstructorMapper(value = AgeOfMajorityRule.class, prefix = "aom")
    @UseRowReducer(PolicyWithAgeOfMajorityRuleReducer.class)
    Optional<GovernancePolicy> findPolicyByStudyId(@Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyByStudyGuid")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    @RegisterConstructorMapper(value = AgeOfMajorityRule.class, prefix = "aom")
    @UseRowReducer(PolicyWithAgeOfMajorityRuleReducer.class)
    Optional<GovernancePolicy> findPolicyByStudyGuid(@Bind("studyGuid") String studyGuid);

    default void addAgeUpCandidate(long studyId, long participantUserId) {
        getStudyGovernanceSql().insertAgeUpCandidate(studyId, participantUserId);
    }

    default void markAgeUpPrepInitiated(Set<Long> candidateIds) {
        DBUtils.checkUpdate(candidateIds.size(), getStudyGovernanceSql().updateAgeUpCandidateInitiatedPrepByIds(true, candidateIds));
    }

    default void removeAgeUpCandidates(Set<Long> candidateIds) {
        DBUtils.checkDelete(candidateIds.size(), getStudyGovernanceSql().deleteAgeUpCandidateByIds(candidateIds));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAllAgeUpCandidatesByStudyId")
    @RegisterConstructorMapper(AgeUpCandidate.class)
    Stream<AgeUpCandidate> findAllAgeUpCandidatesByStudyId(@Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryAgeUpCandidateByStudyIdAndUserId")
    @RegisterConstructorMapper(AgeUpCandidate.class)
    Optional<AgeUpCandidate> findAgeUpCandidate(@Bind("studyId") long studyId, @Bind("userId") long participantUserId);

    class PolicyWithAgeOfMajorityRuleReducer implements LinkedHashMapRowReducer<Long, GovernancePolicy> {
        @Override
        public void accumulate(Map<Long, GovernancePolicy> container, RowView view) {
            long policyId = view.getColumn("study_governance_policy_id", Long.class);
            GovernancePolicy policy = container.computeIfAbsent(policyId, id -> view.getRow(GovernancePolicy.class));
            if (view.getColumn("aom_age_of_majority_rule_id", Long.class) != null) {
                policy.addAgeOfMajorityRule(view.getRow(AgeOfMajorityRule.class));
            }
        }
    }
}

package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.governance.AgeOfMajorityRule;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface StudyGovernanceDao extends SqlObject {

    Logger LOG = LoggerFactory.getLogger(StudyGovernanceDao.class);

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

    /**
     * Deletes the policy and any related rules.
     */
    default void deletePolicy(long policyId) {
        var studyGovernanceSql = getStudyGovernanceSql();
        int numRulesDeleted = studyGovernanceSql.deleteRulesForPolicy(policyId);
        LOG.info("Deleted {} rules for policy {}.", numRulesDeleted, policyId);
        int numPoliciesDeleted = studyGovernanceSql.deletePolicy(policyId);
        if (numPoliciesDeleted > 1) {
            throw new DaoException("Deleted " + numPoliciesDeleted + " instead of a single policy for " + policyId);
        }
        LOG.info("Deleted policy {}.", policyId);
    }

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

package org.broadinstitute.ddp.db.dao;

import java.util.Optional;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pex.Expression;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface StudyGovernanceDao extends SqlObject {

    @CreateSqlObject
    JdbiExpression getJdbiExpression();

    @CreateSqlObject
    StudyGovernanceSql getStudyGovernanceSql();

    default GovernancePolicy createPolicy(GovernancePolicy policy) {
        Expression scguExpr = getJdbiExpression().insertExpression(policy.getShouldCreateGovernedUserExpr().getText());
        long policyId = getStudyGovernanceSql().insertPolicy(true, policy.getStudyId(), null, scguExpr.getId());
        return findPolicyById(policyId).orElseThrow(() -> new DaoException("Could not find study governance policy with id " + policyId));
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyById")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    Optional<GovernancePolicy> findPolicyById(@Bind("id") long policyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyByStudyId")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    Optional<GovernancePolicy> findPolicyByStudyId(@Bind("studyId") long studyId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryPolicyByStudyGuid")
    @RegisterConstructorMapper(Expression.class)
    @RegisterConstructorMapper(GovernancePolicy.class)
    Optional<GovernancePolicy> findPolicyByStudyGuid(@Bind("studyGuid") String studyGuid);
}

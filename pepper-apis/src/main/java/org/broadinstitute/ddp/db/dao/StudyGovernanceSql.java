package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.Define;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface StudyGovernanceSql extends SqlObject {

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertPolicyByStudyIdOrGuid")
    long insertPolicy(
            @Define("byStudyId") boolean byStudyId,
            @Bind("studyId") Long studyId,
            @Bind("studyGuid") String studyGuid,
            @Bind("shouldCreateGovernedUserExprId") long shouldCreateGovernedUserExprId);

    @GetGeneratedKeys
    @UseStringTemplateSqlLocator
    @SqlUpdate("insertAgeOfMajorityRule")
    long insertAgeOfMajorityRule(
            @Bind("policyId") long policyId,
            @Bind("condition") String condition,
            @Bind("age") int age,
            @Bind("prepMonths") Integer prepMonths,
            @Bind("order") int order);

    @SqlUpdate("delete from user_study_governance where user_study_governance_id = :policyId")
    int deletePolicy(@Bind("policyId") long policyId);

    @SqlUpdate("delete from age_of_majority_rule where study_governance_policy_id = :policyId")
    int deleteRulesForPolicy(@Bind("policyId") long policyId);
}

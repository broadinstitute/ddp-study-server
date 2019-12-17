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
}

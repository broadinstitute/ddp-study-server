package org.broadinstitute.ddp.db.dao;

import java.util.Set;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.customizer.BindList.EmptyHandling;
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

    @GetGeneratedKeys
    @SqlUpdate("insert into age_up_candidate (study_id, participant_user_id) values (:studyId, :userId)")
    long insertAgeUpCandidate(@Bind("studyId") long studyId, @Bind("userId") long participantUserId);

    @SqlUpdate("update age_up_candidate set initiated_preparation = :initiated where age_up_candidate_id in (<ids>)")
    int updateAgeUpCandidateInitiatedPrepByIds(
            @Bind("initiated") boolean initiatedPreparation,
            @BindList(value = "ids", onEmpty = EmptyHandling.NULL) Set<Long> candidateIds);

    @SqlUpdate("delete from age_up_candidate where age_up_candidate_id in (<ids>)")
    int deleteAgeUpCandidateByIds(@BindList(value = "ids", onEmpty = EmptyHandling.NULL) Set<Long> candidateIds);
}

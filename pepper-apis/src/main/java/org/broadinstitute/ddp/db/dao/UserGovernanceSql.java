package org.broadinstitute.ddp.db.dao;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface UserGovernanceSql extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into user_governance (operator_user_id, participant_user_id, alias, is_active)"
            + " values (:operatorUserId, :participantUserId, :alias, :isActive)")
    long insertGovernance(
            @Bind("operatorUserId") long proxyUserId,
            @Bind("participantUserId") long governedUserId,
            @Bind("alias") String alias,
            @Bind("isActive") boolean isActive);

    @GetGeneratedKeys
    @SqlUpdate("insert into user_study_governance (user_governance_id, umbrella_study_id) values (:governanceId, :studyId)")
    long insertStudyGovernanceByStudyId(@Bind("governanceId") long governanceId, @Bind("studyId") long studyId);

    @GetGeneratedKeys
    @SqlUpdate("insert into user_study_governance (user_governance_id, umbrella_study_id)"
            + " values (:governanceId, (select umbrella_study_id from umbrella_study where guid = :studyGuid))")
    long insertStudyGovernanceByStudyGuid(@Bind("governanceId") long governanceId, @Bind("studyGuid") String studyGuid);

    @SqlUpdate("update user_governance set is_active = :isActive where user_governance_id = :governanceId")
    int updateGovernanceIsActiveById(@Bind("governanceId") long governanceId, @Bind("isActive") boolean isActive);

    @SqlUpdate("delete from user_governance where user_governance_id = :governanceId")
    int deleteGovernanceById(@Bind("governanceId") long governanceId);

    @SqlUpdate("delete from user_study_governance where user_governance_id = :governanceId")
    int deleteStudyGovernancesByGovernanceId(@Bind("governanceId") long governanceId);

    @SqlUpdate("delete from user_governance where operator_user_id = :operatorUserId")
    int deleteAllGovernancesByOperatorUserId(@Bind("operatorUserId") long operatorUserId);

    @SqlUpdate("delete from user_study_governance where user_governance_id in ("
            + "        select user_governance_id from user_governance where operator_user_id = :operatorUserId)")
    int deleteAllStudyGovernancesByOperatorUserId(@Bind("operatorUserId") long operatorUserId);
}

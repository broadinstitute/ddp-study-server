package org.broadinstitute.ddp.db.dao;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.model.user.User;
import org.jdbi.v3.core.result.LinkedHashMapRowReducer;
import org.jdbi.v3.core.result.RowView;
import org.jdbi.v3.sqlobject.CreateSqlObject;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.jdbi.v3.sqlobject.statement.UseRowReducer;
import org.jdbi.v3.stringtemplate4.UseStringTemplateSqlLocator;

public interface UserGovernanceDao extends SqlObject {

    @CreateSqlObject
    UserDao getUserDao();

    @CreateSqlObject
    UserGovernanceSql getUserGovernanceSql();

    default Governance createGovernedUser(long clientId, long proxyUserId, String alias) {
        User governedUser = getUserDao().createUser(clientId, null);
        long governanceId = getUserGovernanceSql().insertGovernance(proxyUserId, governedUser.getId(), alias, true);
        CacheService.getInstance().modelUpdated(ModelChangeType.USER, getHandle(), proxyUserId);
        return findGovernanceById(governanceId).orElseThrow(() -> new DaoException("Could not find governance with id " + governanceId));
    }

    default Governance createGovernedUserWithGuidAlias(long clientId, long proxyUserId) {
        User governedUser = getUserDao().createUser(clientId, null);
        String alias = governedUser.getGuid();
        long governanceId = getUserGovernanceSql().insertGovernance(proxyUserId, governedUser.getId(), alias, true);
        CacheService.getInstance().modelUpdated(ModelChangeType.USER, getHandle(), proxyUserId);
        return findGovernanceById(governanceId).orElseThrow(() -> new DaoException("Could not find governance with id " + governanceId));
    }

    default long assignProxy(String alias, long proxyUserId, long governedUserId) {
        CacheService.getInstance().modelUpdated(ModelChangeType.USER, getHandle(), proxyUserId);
        return getUserGovernanceSql().insertGovernance(proxyUserId, governedUserId, alias, true);
    }

    default void unassignProxy(long governanceId) {
        DBUtils.checkDelete(1, getUserGovernanceSql().deleteGovernanceById(governanceId));
    }

    default void enableProxy(long governanceId) {
        DBUtils.checkUpdate(1, getUserGovernanceSql().updateGovernanceIsActiveById(governanceId, true));
    }

    default void disableProxy(long governanceId) {
        DBUtils.checkUpdate(1, getUserGovernanceSql().updateGovernanceIsActiveById(governanceId, false));
    }

    @SqlUpdate("update user_governance as ug"
            + "   join user_study_governance as usg on usg.user_governance_id = ug.user_governance_id"
            + "    set ug.is_active = false"
            + "  where usg.umbrella_study_id = :studyId"
            + "    and ug.participant_user_id = :participantId"
            + "    and ug.is_active = true")
    int disableActiveProxies(@Bind("participantId") long participantId, @Bind("studyId") long studyId);

    default long grantGovernedStudy(long governanceId, long studyId) {
        return getUserGovernanceSql().insertStudyGovernanceByStudyId(governanceId, studyId);
    }

    default long grantGovernedStudy(long governanceId, String studyGuid) {
        return getUserGovernanceSql().insertStudyGovernanceByStudyGuid(governanceId, studyGuid);
    }

    default int deleteAllGovernancesForProxy(long proxyUserId) {
        return getUserGovernanceSql().deleteAllGovernancesByOperatorUserId(proxyUserId);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesWithStudiesById")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Optional<Governance> findGovernanceById(@Bind("id") long governanceId);

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesWithStudiesByOperatorGuid")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Stream<Governance> findGovernancesByProxyGuid(@Bind("operatorUserGuid") String proxyUserGuid);

    default Stream<Governance> findActiveGovernancesByProxyGuid(String proxyUserGuid) {
        return findGovernancesByProxyGuid(proxyUserGuid).filter(Governance::isActive);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesWithStudiesByOperatorAndStudyGuids")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Stream<Governance> findGovernancesByProxyAndStudyGuids(
            @Bind("operatorUserGuid") String proxyUserGuid,
            @Bind("studyGuid") String studyGuid);

    default Stream<Governance> findActiveGovernancesByProxyAndStudyGuids(String proxyUserGuid, String studyGuid) {
        return findGovernancesByProxyAndStudyGuids(proxyUserGuid, studyGuid).filter(Governance::isActive);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesByParticipantAndStudyGuids")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Stream<Governance> findGovernancesByParticipantAndStudyGuids(
            @Bind("participantGuid") String participantGuid,
            @Bind("studyGuid") String studyGuid);

    default Stream<Governance> findActiveGovernancesByParticipantAndStudyGuids(String participantGuid, String studyGuid) {
        return findGovernancesByParticipantAndStudyGuids(participantGuid, studyGuid).filter(Governance::isActive);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesByParticipantAndStudyIds")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Stream<Governance> findGovernancesByParticipantAndStudyIds(
            @Bind("participantId") long participantId,
            @Bind("studyId") long studyId);

    default Stream<Governance> findActiveGovernancesByParticipantAndStudyIds(long participantId, long studyId) {
        return findGovernancesByParticipantAndStudyIds(participantId, studyId).filter(Governance::isActive);
    }

    @UseStringTemplateSqlLocator
    @SqlQuery("queryGovernancesByStudyGuid")
    @RegisterConstructorMapper(Governance.class)
    @RegisterConstructorMapper(GrantedStudy.class)
    @UseRowReducer(GovernanceWithStudiesReducer.class)
    Stream<Governance> findGovernancesByStudyGuid(@Bind("studyGuid") String studyGuid);

    default Stream<Governance> findActiveGovernancesByStudyGuid(String studyGuid) {
        return findGovernancesByStudyGuid(studyGuid).filter((Governance::isActive));
    }

    class GovernanceWithStudiesReducer implements LinkedHashMapRowReducer<Long, Governance> {
        @Override
        public void accumulate(Map<Long, Governance> container, RowView view) {
            long governanceId = view.getColumn("user_governance_id", Long.class);
            Long studyGovernanceId = view.getColumn("user_study_governance_id", Long.class);
            Governance governance = container.computeIfAbsent(governanceId, key -> view.getRow(Governance.class));
            if (studyGovernanceId != null) {
                governance.addGrantedStudy(view.getRow(GrantedStudy.class));
            }
        }
    }
}

package org.broadinstitute.ddp.db.dao;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.broadinstitute.ddp.cache.ModelChangeType;
import org.broadinstitute.ddp.model.governance.Governance;
import org.jdbi.v3.core.Handle;

public class UserGovernanceCachedDao extends SQLObjectWrapper<UserGovernanceDao> implements UserGovernanceDao {
    public UserGovernanceCachedDao(Handle handle) {
        super(handle, UserGovernanceDao.class);
    }

    public Governance createGovernedUser(long clientId, long proxyUserId, String alias) {
        var result = target.createGovernedUser(clientId, proxyUserId, alias);
        notifyModelUpdated(ModelChangeType.USER, proxyUserId);
        return result;
    }
    public Governance createGovernedUserWithGuidAlias(long clientId, long proxyUserId) {
        var result = target.createGovernedUserWithGuidAlias(clientId, proxyUserId);
        notifyModelUpdated(ModelChangeType.USER, proxyUserId);
        return result;
    }

    @Override
    public UserDao getUserDao() {
        return target.getUserDao();
    }

    @Override
    public UserGovernanceSql getUserGovernanceSql() {
        return target.getUserGovernanceSql();
    }

    @Override
    public int disableActiveProxies(long participantId, long studyId) {
        List<Long> proxyIds = findGovernancesByParticipantAndStudyIds(participantId, studyId)
                .map(governance -> governance.getProxyUserId())
                .collect(Collectors.toList());
        var result = target.disableActiveProxies(participantId, studyId);
        proxyIds.forEach(proxyId -> notifyModelUpdated(ModelChangeType.USER, proxyId));
        notifyModelUpdated(ModelChangeType.USER, participantId);
        return result;
    }

    @Override
    public Optional<Governance> findGovernanceById(long governanceId) {
        return target.findGovernanceById(governanceId);
    }

    @Override
    public Stream<Governance> findGovernancesByProxyGuid(String proxyUserGuid) {
        return target.findGovernancesByProxyGuid(proxyUserGuid);
    }

    @Override
    public Stream<Governance> findGovernancesByProxyAndStudyGuids(String proxyUserGuid, String studyGuid) {
        return target.findGovernancesByProxyAndStudyGuids(proxyUserGuid, studyGuid);
    }

    @Override
    public Stream<Governance> findGovernancesByParticipantAndStudyGuids(String participantGuid, String studyGuid) {
        return target.findGovernancesByParticipantAndStudyGuids(participantGuid, studyGuid);
    }

    @Override
    public Stream<Governance> findGovernancesByParticipantAndStudyIds(long participantId, long studyId) {
        return target.findGovernancesByParticipantAndStudyIds(participantId, studyId);
    }

    @Override
    public Stream<Governance> findGovernancesByStudyGuid(String studyGuid) {
        return target.findGovernancesByStudyGuid(studyGuid);
    }
}

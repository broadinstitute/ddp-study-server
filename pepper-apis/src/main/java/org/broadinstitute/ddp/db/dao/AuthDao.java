package org.broadinstitute.ddp.db.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.security.ParticipantAccess;
import org.broadinstitute.ddp.security.UserPermissions;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

public interface AuthDao extends SqlObject {

    @GetGeneratedKeys
    @SqlUpdate("insert into study_admin (user_id, umbrella_study_id) values (:userId, :studyId)")
    long assignStudyAdmin(@Bind("userId") long userId, @Bind("studyId") long studyId);

    @SqlUpdate("delete from study_admin where user_id = :userId")
    int removeAdminFromAllStudies(@Bind("userId") long userId);

    default UserPermissions findUserPermissions(String operatorGuid, String auth0ClientId, String auth0Domain) {
        UserClientStatus status = findUserClientStatus(operatorGuid, auth0ClientId, auth0Domain)
                .orElseThrow(() -> {
                    String msg = String.format("Could not find revocation status for operator %s and client %s and tenant %s",
                            operatorGuid, auth0ClientId, auth0Domain);
                    return new DaoException(msg);
                });

        List<String> clientStudyGuids = getHandle().attach(JdbiClientUmbrellaStudy.class)
                .findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(auth0ClientId, auth0Domain);

        Map<String, ParticipantAccess> participants = new HashMap<>();
        List<Governance> governances = getHandle().attach(UserGovernanceDao.class)
                .findActiveGovernancesByProxyGuid(operatorGuid)
                .collect(Collectors.toList());
        for (Governance governance : governances) {
            String participantGuid = governance.getGovernedUserGuid();
            ParticipantAccess access = participants.computeIfAbsent(participantGuid, ParticipantAccess::new);
            for (GrantedStudy study : governance.getGrantedStudies()) {
                access.addStudyGuid(study.getStudyGuid());
            }
        }

        List<String> adminStudyGuids = findAdminAccessibleStudyGuids(operatorGuid);

        return new UserPermissions(operatorGuid, status.isAccountLocked(), status.isRevoked(),
                clientStudyGuids, new ArrayList<>(participants.values()), adminStudyGuids);
    }

    @SqlQuery("select us.guid from study_admin as sa"
            + "  join umbrella_study as us on us.umbrella_study_id = sa.umbrella_study_id"
            + "  join user as u on u.user_id = sa.user_id"
            + " where u.guid = :guid")
    List<String> findAdminAccessibleStudyGuids(@Bind("guid") String operatorGuid);

    @SqlQuery("select u.is_locked, c.is_revoked from user u, client c, auth0_tenant t"
            + " where u.guid = :guid and c.auth0_client_id = :client and t.auth0_domain = :domain"
            + "   and c.auth0_tenant_id = t.auth0_tenant_id")
    @RegisterConstructorMapper(UserClientStatus.class)
    Optional<UserClientStatus> findUserClientStatus(
            @Bind("guid") String operatorGuid,
            @Bind("client") String auth0ClientId,
            @Bind("domain") String auth0Domain);

    class UserClientStatus {

        private boolean isAccountLocked;
        private boolean isRevoked;

        @JdbiConstructor
        public UserClientStatus(@ColumnName("is_locked") boolean isAccountLocked, @ColumnName("is_revoked") boolean isRevoked) {
            this.isAccountLocked = isAccountLocked;
            this.isRevoked = isRevoked;
        }

        boolean isAccountLocked() {
            return isAccountLocked;
        }

        boolean isRevoked() {
            return isRevoked;
        }
    }
}

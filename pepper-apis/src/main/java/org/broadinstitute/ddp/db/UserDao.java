package org.broadinstitute.ddp.db;

import static org.broadinstitute.ddp.logging.LogUtil.getSecureLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.User;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.governance.GrantedStudy;
import org.broadinstitute.ddp.security.ParticipantAccess;
import org.broadinstitute.ddp.security.UserPermissions;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Db utilities relating to registration of operators
 * and participants.
 */
public class UserDao {

    private static final Logger LOG = LoggerFactory.getLogger(UserDao.class);

    public static final long EXPIRATION_DURATION_MILLIS = TimeUnit.HOURS.toMillis(24);

    private final String insertUserStmt;
    private final String hasAdminAccessQuery;
    private final String userClientRevocationQuery;

    /**
     * Set up needed sql queries.
     */
    public UserDao(String insertUserStmt,
                   String hasAdminAccessQuery,
                   String userClientRevocationQuery
    ) {
        this.insertUserStmt = insertUserStmt;
        this.hasAdminAccessQuery = hasAdminAccessQuery;
        this.userClientRevocationQuery = userClientRevocationQuery;
    }

    /**
     * Create a new temporary user.
     *
     * @param handle        the database handle
     * @param auth0ClientId the client
     * @return temporary user
     */
    public UserDto createTemporaryUser(Handle handle, String auth0ClientId) {
        try {
            User user = insertNewUser(handle, auth0ClientId, null, true);
            return handle.attach(JdbiUser.class).findByUserGuid(user.getDdpUserId());
        } catch (SQLException e) {
            throw new DaoException(String.format("Could not create temporary user with auth0ClientId '%s'", auth0ClientId));
        }
    }

    private User insertNewUser(Handle handle, String auth0ClientId, String auth0UserId, boolean isTemporary) throws SQLException {
        // todo arz lock user table first
        String userGuid = DBUtils.uniqueUserGuid(handle);
        String userHruid = DBUtils.uniqueUserHruid(handle);
        long now = Instant.now().toEpochMilli();
        // otherwise repeat attempts at a new guid until one is found

        Long expiresAt = isTemporary ? now + EXPIRATION_DURATION_MILLIS : null;

        try (PreparedStatement insertUser = handle.getConnection().prepareStatement(insertUserStmt)) {
            // todo arz use auth0 api to make the user for real.  when returned, insert it
            insertUser.setString(1, auth0UserId);
            insertUser.setString(2, userGuid);
            insertUser.setString(3, userHruid);
            insertUser.setLong(4, now);
            insertUser.setLong(5, now);
            StatementUtils.setLong(insertUser, 6, expiresAt);
            insertUser.setString(7, auth0ClientId);
            int rowsInserted = insertUser.executeUpdate();
            if (rowsInserted != 1) {
                getSecureLog().error("Could not create new account using client {} because {} rows were inserted",
                        auth0ClientId, rowsInserted);
                throw new DaoException("Failed to save account information.  " + rowsInserted + " rows were updated");
            }
        }

        // todo arz invent a two-tier exception that will write sensitive data to one log and public data to another log

        return new User(userGuid);
    }

    /**
     * Returns user permissions for the given operator user guid.
     */
    public UserPermissions queryUserPermissionsByOperatorGuidAndClientId(Handle handle, String operatorGuid,
                                                                         String auth0ClientId) {
        ClientStatus clientStatus = handle.select(userClientRevocationQuery, operatorGuid, auth0ClientId)
                .map(new UserClientStatusMapper())
                .findFirst()
                .orElseThrow(() -> {
                    String msg = String.format("Could not find revocation status for operator %s and client auth0ClientId %s",
                            operatorGuid, auth0ClientId);
                    return new DaoException(msg);
                });
        List<String> clientStudyGuids = handle.attach(JdbiClientUmbrellaStudy.class).findPermittedStudyGuidsByAuth0ClientId(auth0ClientId);

        Map<String, ParticipantAccess> participants = new HashMap<>();
        List<Governance> governances = handle.attach(UserGovernanceDao.class)
                .findActiveGovernancesByProxyGuid(operatorGuid)
                .collect(Collectors.toList());
        for (Governance governance : governances) {
            String participantGuid = governance.getGovernedUserGuid();
            ParticipantAccess access = participants.computeIfAbsent(participantGuid, ParticipantAccess::new);
            for (GrantedStudy study : governance.getGrantedStudies()) {
                access.addStudyGuid(study.getStudyGuid());
            }
        }

        Collection<String> adminStudyGuids = new ArrayList<>();
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(hasAdminAccessQuery)) {
            stmt.setString(1, operatorGuid);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                adminStudyGuids.add(rs.getString("guid"));
            }
        } catch (SQLException e) {
            throw new DaoException("could not determine if user has admin access to study", e);
        }

        return new UserPermissions(operatorGuid, clientStatus.isAccountLocked(), clientStatus.isRevoked(),
                clientStudyGuids, participants.values(), adminStudyGuids);
    }

    private static class ClientStatus {

        private boolean isAccountLocked;
        private boolean isRevoked;

        ClientStatus(boolean isAccountLocked, boolean isRevoked) {
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

    private static class UserClientStatusMapper implements RowMapper<ClientStatus> {

        @Override
        public ClientStatus map(ResultSet rs, StatementContext ctx) throws SQLException {
            return new ClientStatus(rs.getBoolean(SqlConstants.IS_USER_LOCKED),
                    rs.getBoolean(SqlConstants.ClientTable.IS_CLIENT_REVOKED));
        }
    }
}

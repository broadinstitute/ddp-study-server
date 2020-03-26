package org.broadinstitute.ddp.db;

import static org.broadinstitute.ddp.logging.LogUtil.getSecureLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.json.Profile;
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
    private final String profileExistsQuery;
    private final String getUserIdFromGuid;
    private final String userClientRevocationQuery;
    private final String patchGenderStmt;
    private final String patchPreferredLanguageStmt;

    /**
     * Set up needed sql queries.
     */
    public UserDao(String insertUserStmt,
                   String hasAdminAccessQuery,
                   String profileExistsQuery,
                   String getUserIdFromGuid,
                   String userClientRevocationQuery,
                   String patchGenderStmt,
                   String patchPreferredLanguageStmt
    ) {
        this.insertUserStmt = insertUserStmt;
        this.hasAdminAccessQuery = hasAdminAccessQuery;
        this.profileExistsQuery = profileExistsQuery;
        this.getUserIdFromGuid = getUserIdFromGuid;
        this.userClientRevocationQuery = userClientRevocationQuery;
        this.patchGenderStmt = patchGenderStmt;
        this.patchPreferredLanguageStmt = patchPreferredLanguageStmt;
    }

    /**
     * Returns the user_id from user table based on guid.
     *
     * @param handle JDBI handle
     * @param guid   the user guid
     * @return the user ID for a the user specified by the given guid
     */
    public Long getUserIdByGuid(Handle handle, String guid) {
        Long userId = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(getUserIdFromGuid)) {
            stmt.setString(1, guid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getLong(1);
                }
                if (rs.next()) {
                    throw new DaoException("Multiple user entries for user with guid " + guid);
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find user id by guid " + guid, e);
        }
        return userId;
    }

    /**
     * Returns true if the profile with the given user id exists.
     *
     * @param handle JDBI handle
     * @param guid   the user guid
     * @return a boolean value indicating whether a user (given by the guid) has an attributed profile
     */
    public boolean doesProfileExist(Handle handle, String guid) throws SQLException {
        boolean profileExists = false;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(profileExistsQuery)) {
            stmt.setString(1, guid);
            try (ResultSet rs = stmt.executeQuery()) {
                profileExists = rs.next();
            }
        }

        return profileExists;
    }

    /**
     * Given an isoLanguageCode, make sure is actual entry is languague_code table and if so, return its primary key.
     * Otherwise, if it's an empty string/ null, return 0 value since it is valid that profile will not
     * have preferred language. For all other cases, return -1
     *
     * @param isoLanguageCode ISO string representation of language, e.g. "en" or "ru"
     * @param handle          JDBI handle
     * @return ID, or indicator of null vs. invalid status
     */
    public boolean isValidLanguage(String isoLanguageCode, Handle handle) throws SQLException {
        if (StringUtils.isBlank(isoLanguageCode)) {
            return true;
        }
        boolean valid = handle.attach(JdbiLanguageCode.class).getLanguageCodeId(isoLanguageCode) != null;
        return valid;
    }

    /**
     * Given a string, make sure is actual value of Profile.Sex
     *
     * @param proposedSex inputted value in JSON by user to indicate biological sex
     * @return boolean value stating if valid string representation of Profile.Sex value
     */
    public boolean isValidSex(String proposedSex) {
        boolean valid = false;
        for (Profile.Sex sex : Profile.Sex.values()) {
            if (proposedSex == null || proposedSex.equals(sex.toString())) {
                valid = true;
                break;
            }
        }
        return valid;
    }


    /**
     * Creates a profile for a user (specified by the guid) based on the information in the profile object.
     * Writes profile information to pepper.
     *
     * @param handle  the JDBC connection
     * @param profile object that holds all relevant attributes for a profile
     * @param guid    the user guid
     * @return Profile object with all attributes of new profile
     */
    public Profile addProfile(Handle handle, Profile profile, String guid) {
        String sex = (profile.getSex() == null) ? null : profile.getSex().name();
        String preferredLanguage = profile.getPreferredLanguage();

        JdbiProfile jdbiProfile = handle.attach(JdbiProfile.class);
        JdbiLanguageCode jdbiLanguageCode = handle.attach(JdbiLanguageCode.class);
        int numRowsInserted = jdbiProfile.insert(
                new UserProfileDto(
                        getUserIdByGuid(handle, guid),
                        profile.getFirstName(),
                        profile.getLastName(),
                        sex,
                        profile.getBirthDate(),
                        jdbiLanguageCode.getLanguageCodeId(preferredLanguage),
                        null,
                        null
                )
        );
        if (numRowsInserted != 1) {
            throw new DaoException("Failed to save profile information.  " + numRowsInserted + " rows were updated");
        }

        return profile;
    }

    /**
     * Given a user guid, looks up profile in pepper and retrieves it if possible.
     *
     * @param handle JDBI handle
     * @param guid   the user guid
     * @return Profile object containing all attributes of retrieved profile
     */
    public Profile getProfile(Handle handle, String guid) {
        UserProfileDto profileDto = handle.attach(JdbiProfile.class).getUserProfileByUserGuid(guid);
        if (profileDto == null) {
            throw new DaoException("Could not find profile for user with guid " + guid);
        }

        LocalDate birthDate = profileDto.getBirthDate();
        Integer birthYear = birthDate == null ? null : birthDate.getYear();
        Integer birthMonth = birthDate == null ? null : birthDate.getMonthValue();
        Integer birthDayInMonth = birthDate == null ? null : birthDate.getDayOfMonth();
        Profile.Sex sex = Profile.Sex.fromString(profileDto.getSex());
        String preferredLanguage = profileDto.getPreferredLanguageCode();
        String firstName = profileDto.getFirstName();
        String lastName = profileDto.getLastName();

        return new Profile(birthDayInMonth, birthMonth, birthYear, sex, preferredLanguage, firstName, lastName);
    }

    private void updateProfileString(Handle handle, long userId, String jsonFieldName, JsonObject payload) {
        JdbiProfile profileDao = handle.attach(JdbiProfile.class);
        if (payload.has(jsonFieldName)) {
            JsonElement jsonValue = payload.get(jsonFieldName);
            int numRowsUpdated = 0;
            String columnValue = null;
            if (!jsonValue.isJsonNull()) {
                if (jsonValue.isJsonPrimitive() && jsonValue.getAsJsonPrimitive().isString()) {
                    columnValue = jsonValue.getAsString();
                } else {
                    throw new IllegalArgumentException("Unknown type for " + jsonFieldName + " with value "
                                                               + jsonValue);
                }
            }
            if (Profile.FIRST_NAME.equals(jsonFieldName)) {
                numRowsUpdated = profileDao.updateFirstName(columnValue, userId);
            } else if (Profile.LAST_NAME.equals(jsonFieldName)) {
                numRowsUpdated = profileDao.updateLastName(columnValue, userId);
            } else {
                throw new IllegalArgumentException("Unknown field " + jsonFieldName + ".  Cannot update profile data "
                                                           + "for user " + userId);
            }
            if (numRowsUpdated != 1) {
                throw new DaoException("Updated " + numRowsUpdated + " rows for " + jsonFieldName + " to "
                                       + columnValue + " for user " + userId);
            }
        }
    }

    private LocalDate parseProfileBirthDate(JsonObject payload) {
        Integer year = null;
        if (payload.has(Profile.BIRTH_YEAR) && !payload.get(Profile.BIRTH_YEAR).isJsonNull()) {
            year = payload.get(Profile.BIRTH_YEAR).getAsInt();
        }

        Integer month = null;
        if (payload.has(Profile.BIRTH_MONTH) && !payload.get(Profile.BIRTH_MONTH).isJsonNull()) {
            month = payload.get(Profile.BIRTH_MONTH).getAsInt();
        }

        Integer day = null;
        if (payload.has(Profile.BIRTH_DAY_IN_MONTH) && !payload.get(Profile.BIRTH_DAY_IN_MONTH).isJsonNull()) {
            day = payload.get(Profile.BIRTH_DAY_IN_MONTH).getAsInt();
        }

        if (year != null && month != null && day != null) {
            try {
                return LocalDate.of(year, month, day);
            } catch (DateTimeException e) {
                throw new DaoException("Invalid birth date", e);
            }
        } else if (year == null && month == null && day == null) {
            return null;
        } else {
            throw new DaoException("Need to provide full birth date");
        }
    }

    /**
     * Given specific attributes to update from JsonObject for a given existing user profile, enter
     * information on pepper.
     *
     * @param handle         JDBI handle
     * @param profileUpdates JsonObject with profile attribute updates
     * @param guid           the user guid
     * @return the full, updated profile that has been modified
     */
    public Profile patchProfile(Handle handle, JsonObject profileUpdates, String guid) throws SQLException {
        Long userId = getUserIdByGuid(handle, guid);

        updateProfileString(handle, userId, Profile.FIRST_NAME, profileUpdates);
        updateProfileString(handle, userId, Profile.LAST_NAME, profileUpdates);

        // walk through all attributes of profile and replace if necessary
        if (profileUpdates.has(Profile.SEX)) {
            String sexStr = null;
            if (!profileUpdates.get(Profile.SEX).isJsonNull()) {
                sexStr = profileUpdates.get(Profile.SEX).getAsString();
            }
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(patchGenderStmt)) {
                if (sexStr == null) {
                    stmt.setNull(1, Types.VARCHAR);
                } else {
                    stmt.setString(1, sexStr);
                }
                stmt.setLong(2, userId);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated != 1) {
                    throw new DaoException("Failed to update sex information.  " + rowsUpdated + " rows were updated");
                }
            }
        }
        if (profileUpdates.has(Profile.PREFERRED_LANGUAGE)) {
            String preferredLanguage = null;
            if (!profileUpdates.get(Profile.PREFERRED_LANGUAGE).isJsonNull()) {
                preferredLanguage = profileUpdates.get(Profile.PREFERRED_LANGUAGE).getAsString();
            }
            boolean valid = isValidLanguage(preferredLanguage, handle);
            if (valid) {
                try (PreparedStatement stmt = handle.getConnection().prepareStatement(patchPreferredLanguageStmt)) {
                    if (preferredLanguage == null) {
                        stmt.setNull(1, Types.VARCHAR);
                    } else {
                        stmt.setString(1, preferredLanguage);
                    }
                    stmt.setLong(2, userId);
                    int rowsUpdated = stmt.executeUpdate();
                    if (rowsUpdated != 1) {
                        throw new DaoException("Failed to update preferred language.  "
                                + rowsUpdated + " rows were updated");
                    }
                }
            } else {
                LOG.error("Tried to update preferred language to value not in pepper.");
            }
        }

        LocalDate birthDate = parseProfileBirthDate(profileUpdates);
        boolean success = handle.attach(JdbiProfile.class).upsertBirthDate(userId, birthDate);
        if (!success) {
            throw new DaoException("Could not update birth date for user with guid " + guid);
        }

        return getProfile(handle, guid);
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
    public UserPermissions queryUserPermissions(
            Handle handle,
            String operatorGuid,
            String auth0ClientId,
            String auth0Domain
    ) {
        ClientStatus clientStatus = handle.select(userClientRevocationQuery, operatorGuid, auth0ClientId)
                .map(new UserClientStatusMapper())
                .findFirst()
                .orElseThrow(() -> {
                    String msg = String.format("Could not find revocation status for operator %s and client auth0ClientId %s",
                            operatorGuid, auth0ClientId);
                    return new DaoException(msg);
                });
        List<String> clientStudyGuids = handle.attach(JdbiClientUmbrellaStudy.class).findPermittedStudyGuidsByAuth0ClientIdAndAuth0Domain(
                auth0ClientId,
                auth0Domain
        );

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

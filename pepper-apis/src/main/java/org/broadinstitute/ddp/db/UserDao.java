package org.broadinstitute.ddp.db;

import static org.broadinstitute.ddp.logging.LogUtil.getSecureLog;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
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
import org.broadinstitute.ddp.constants.SqlConstants.UserGovernanceTable;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiLanguageCode;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.exception.UserExistsException;
import org.broadinstitute.ddp.json.GovernedParticipant;
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
    private final String checkUserGuidQuery;
    private final String updateUserFirstAndLastName;
    private final String insertGovernedUserStmt;
    private final String hasAdminAccessQuery;
    private final String userExistsQuery;
    private final String userGuidForAuth0IdQuery;
    private final String addProfileStmt;
    private final String profileExistsQuery;
    private final String getUserIdFromGuid;
    private final String getUserIdFromHruid;
    private final String getGuidFromUserId;
    private final String getProfileStmt;
    private final String userExistsGuidQuery;
    private final String governanceAliasExistsQuery;
    private final String userClientRevocationQuery;
    private final String getAllGovParticipantsQuery;
    private final String patchGenderStmt;
    private final String patchBirthYearStmt;
    private final String patchBirthMonthStmt;
    private final String patchBirthDayInMonthStmt;
    private final String patchPreferredLanguageStmt;

    /**
     * Set up needed sql queries.
     */
    public UserDao(String insertUserStmt,
                   String updateUserFirstAndLastName,
                   String checkUserGuidQuery,
                   String insertGovernedUserStmt,
                   String hasAdminAccessQuery,
                   String userExistsQuery,
                   String userGuidForAuth0IdQuery,
                   String addProfileStmt,
                   String profileExistsQuery,
                   String getUserIdFromGuid,
                   String getUserIdFromHruid,
                   String getGuidFromUserId,
                   String getProfileStmt,
                   String userExistsGuidQuery,
                   String governanceAliasExistsQuery,
                   String userClientRevocationQuery,
                   String getAllGovParticipantsQuery,
                   String patchGenderStmt,
                   String patchBirthYearStmt,
                   String patchBirthMonthStmt,
                   String patchBirthDayInMonthStmt,
                   String patchPreferredLanguageStmt
    ) {
        this.insertUserStmt = insertUserStmt;
        this.updateUserFirstAndLastName = updateUserFirstAndLastName;
        this.checkUserGuidQuery = checkUserGuidQuery;
        this.insertGovernedUserStmt = insertGovernedUserStmt;
        this.hasAdminAccessQuery = hasAdminAccessQuery;
        this.userExistsQuery = userExistsQuery;
        this.userGuidForAuth0IdQuery = userGuidForAuth0IdQuery;
        this.addProfileStmt = addProfileStmt;
        this.profileExistsQuery = profileExistsQuery;
        this.getUserIdFromGuid = getUserIdFromGuid;
        this.getUserIdFromHruid = getUserIdFromHruid;
        this.getGuidFromUserId = getGuidFromUserId;
        this.getProfileStmt = getProfileStmt;
        this.userExistsGuidQuery = userExistsGuidQuery;
        this.governanceAliasExistsQuery = governanceAliasExistsQuery;
        this.userClientRevocationQuery = userClientRevocationQuery;
        this.getAllGovParticipantsQuery = getAllGovParticipantsQuery;
        this.patchGenderStmt = patchGenderStmt;
        this.patchBirthYearStmt = patchBirthYearStmt;
        this.patchBirthMonthStmt = patchBirthMonthStmt;
        this.patchBirthDayInMonthStmt = patchBirthDayInMonthStmt;
        this.patchPreferredLanguageStmt = patchPreferredLanguageStmt;
    }

    /**
     * Returns true if the user with the given auth0 user id exists.
     */
    private boolean doesUserExistByAuth0Id(Handle handle, String auth0Domain, String auth0UserId) {
        boolean userExists;
        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(userExistsQuery)) {
                stmt.setString(1, auth0UserId);
                stmt.setString(2, auth0Domain);
                try (ResultSet rs = stmt.executeQuery()) {
                    userExists = rs.next();
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not determine whether existing user " + auth0UserId + " exists", e);
        }
        return userExists;
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
     * Returns the user_id from user table based on hruid.
     *
     * @param handle JDBI handle
     * @param hruid   the user hruid
     * @return the user ID for a the user specified by the given hruid
     */
    public Long getUserIdByHruid(Handle handle, String hruid) throws SQLException {
        Long userId = null;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(getUserIdFromHruid)) {
            stmt.setString(1, hruid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    userId = rs.getLong(1);
                }
                if (rs.next()) {
                    throw new DaoException("Multiple entries for user with hruid " + hruid);
                }
            }
        }
        return userId;
    }

    /**
     * Returns the user guid from the user table based on user id.
     *
     * @param handle JDBI handle
     * @param userId the user id
     * @return the guid for the user specified by the given ID
     */
    public String getUserGuidForUserId(Handle handle, Long userId) {
        String userGuid = null;

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(getGuidFromUserId)) {
            stmt.setLong(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                userGuid = rs.getString(SqlConstants.PARTICIPANT_GUID);
                if (rs.next()) {
                    throw new DaoException("too many users for given id " + userId);
                }
            } else {
                throw new DaoException("No corresponding guid for userId " + userId);
            }
        } catch (SQLException e) {
            throw new DaoException("Cannot find guid for corresponding userId: " + userId, e);
        }

        return userGuid;
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
    public Profile addProfile(Handle handle, Profile profile, String guid) throws SQLException {
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
                        profile.getBirthYear(),
                        profile.getBirthMonth(),
                        profile.getBirthDayInMonth(),
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
        String sexString = "";
        //default values of a profile should be null
        Profile.Sex sex;
        Integer birthDayInMonth = null;
        Integer birthMonth = null;
        Integer birthYear = null;
        String preferredLanguage = null;
        String firstName = null;
        String lastName = null;

        try (PreparedStatement stmt = handle.getConnection().prepareStatement(getProfileStmt)) {
            stmt.setString(1, guid);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    sexString = rs.getString(SqlConstants.SEX);
                    birthDayInMonth = (Integer) rs.getObject(SqlConstants.BIRTH_DAY_IN_MONTH);
                    birthMonth = (Integer) rs.getObject(SqlConstants.BIRTH_MONTH);
                    birthYear = (Integer) rs.getObject(SqlConstants.BIRTH_YEAR);
                    preferredLanguage = rs.getString(SqlConstants.ISO_LANGUAGE_CODE);
                    firstName = rs.getString(SqlConstants.UserProfileTable.FIRST_NAME);
                    lastName = rs.getString(SqlConstants.UserProfileTable.LAST_NAME);
                } else {
                    throw new NotFoundException("Could not find profile for user with GUID " + guid);
                }
            }
        } catch (RuntimeException | SQLException e) {
            throw new DaoException("Could not retrieve profile row for user " + guid, e);
        }

        sex = Profile.Sex.fromString(sexString);

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
        if (profileUpdates.has(Profile.BIRTH_DAY_IN_MONTH)) {
            Integer birthDayInMonth = null;
            if (!profileUpdates.get(Profile.BIRTH_DAY_IN_MONTH).isJsonNull()) {
                birthDayInMonth = (Integer) profileUpdates.get(Profile.BIRTH_DAY_IN_MONTH).getAsInt();
            }
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(patchBirthDayInMonthStmt)) {
                StatementUtils.setInteger(stmt, 1, birthDayInMonth);
                stmt.setLong(2, userId);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated != 1) {
                    throw new DaoException("Failed to update birth day in month profile information.  "
                            + rowsUpdated + " rows were updated");
                }
            }
        }
        if (profileUpdates.has(Profile.BIRTH_MONTH)) {
            Integer birthMonth = null;
            if (!profileUpdates.get(Profile.BIRTH_MONTH).isJsonNull()) {
                birthMonth = (Integer) profileUpdates.get(Profile.BIRTH_MONTH).getAsInt();
            }
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(patchBirthMonthStmt)) {
                StatementUtils.setInteger(stmt, 1, birthMonth);
                stmt.setLong(2, userId);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated != 1) {
                    throw new DaoException("Failed to update birth month profile information.  "
                            + rowsUpdated + " rows were updated");
                }
            }
        }
        if (profileUpdates.has(Profile.BIRTH_YEAR)) {
            Integer birthYear = null;
            if (!profileUpdates.get(Profile.BIRTH_YEAR).isJsonNull()) {
                birthYear = (Integer) profileUpdates.get(Profile.BIRTH_YEAR).getAsInt();
            }
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(patchBirthYearStmt)) {
                StatementUtils.setInteger(stmt, 1, birthYear);
                stmt.setLong(2, userId);
                int rowsUpdated = stmt.executeUpdate();
                if (rowsUpdated != 1) {
                    throw new DaoException("Failed to update birth year profile information.  "
                            + rowsUpdated + " rows were updated");
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
        return getProfile(handle, guid);
    }

    /**
     * Determines if given alias can be used to label a governed participant of governing user.
     */
    public boolean isGovernanceAliasUnique(Handle handle, String governingUserGuid, String alias) {
        boolean aliasExists = false;
        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(governanceAliasExistsQuery)) {
                stmt.setString(1, governingUserGuid);
                stmt.setString(2, alias);
                try (ResultSet rs = stmt.executeQuery()) {
                    aliasExists = rs.next();
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not determine uniqueness of alias for user " + governingUserGuid, e);
        }
        return !aliasExists;
    }

    /**
     * Creates a new participant and adds the participant to the governing user's list of governed participants.
     *
     * @param handle            JDBI handle
     * @param clientGuid        the client guid
     * @param governingUserGuid the governing operator's guid
     * @param alias             the nickname for this governance relationship
     * @return the new participant user
     */
    public User createGovernedParticipant(Handle handle, String clientGuid, String governingUserGuid, String alias) {
        User newParticipant = null;

        try {
            newParticipant = insertNewUser(handle, clientGuid, null);
        } catch (SQLException e) {
            throw new DaoException("Could not create new governed participant for operator " + governingUserGuid, e);
        }

        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(insertGovernedUserStmt)) {
                stmt.setString(1, governingUserGuid);
                stmt.setString(2, newParticipant.getDdpUserId());
                stmt.setString(3, alias);
                stmt.setBoolean(4, true);
                int numRowsInserted = stmt.executeUpdate();

                if (numRowsInserted != 1) {
                    getSecureLog().error("Could not add governed participant with alias '{}' because {} rows "
                            + "were inserted", alias, numRowsInserted);
                    LOG.error("Failed to add governed participant");
                } else {
                    getSecureLog().info("Added governed participant {} with alias '{}'",
                            newParticipant.getDdpUserId(), alias);
                    LOG.info("Added governed participant {}", newParticipant.getDdpUserId());
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not add governed participant for operator " + governingUserGuid, e);
        }

        return newParticipant;
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

    private User insertNewUser(Handle handle, String auth0ClientId, String auth0UserId) throws SQLException {
        return insertNewUser(handle, auth0ClientId, auth0UserId, false);
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
     * Updates or inserts first and last name into user_profile.
     * @param handle the jdbi handle
     * @param userGuid userGuid
     * @param firstName user first name
     * @param lastName user last name
     * @throws SQLException if error executing
     */
    public void upsertUserName(Handle handle, String userGuid, String firstName, String lastName) throws SQLException {
        try (PreparedStatement updateStmt = handle.getConnection().prepareStatement(updateUserFirstAndLastName)) {
            updateStmt.setString(1, firstName);
            updateStmt.setString(2, lastName);
            updateStmt.setString(3, userGuid);
            int returnValue = updateStmt.executeUpdate();
            LOG.info("Database returned: {} while upserting first name and last name into user_profile", returnValue);
        }
    }

    /**
     * Register user by client ID and user ID.
     *
     * @param handle        the JDBC connection
     * @param auth0Domain the auth0 domain
     * @param auth0ClientId auth0 client ID
     * @param auth0UserId   auth 0 user ID
     * @return new user object
     */
    public User registerUser(Handle handle, String auth0Domain, String auth0ClientId, String auth0UserId) throws
            UserExistsException {
        if (doesUserExistByAuth0Id(handle, auth0Domain, auth0UserId)) {
            throw new UserExistsException(auth0UserId);
        } else {
            User user = null;
            try {
                user = insertNewUser(handle, auth0ClientId, auth0UserId);
            } catch (SQLException e) {
                throw new DaoException("Could not create user " + auth0UserId + " using client " + auth0ClientId, e);
            }
            getSecureLog().info("Created ddp user {} for {}", user.getDdpUserId(), auth0UserId);
            return user;
        }
    }

    /**
     * Get an operator's list of governed participants.
     *
     * @param handle       JDBI handle
     * @param operatorGuid the governing operator's guid
     * @return list of governed participants and their aliases
     */
    public List<GovernedParticipant> getAllGovernedParticipantsByOperatorGuid(Handle handle, String operatorGuid) {
        List<GovernedParticipant> participants = new ArrayList<>();
        try {
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(getAllGovParticipantsQuery)) {
                stmt.setString(1, operatorGuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String guid = rs.getString(SqlConstants.DDP_USER_GUID);
                        String alias = rs.getString(UserGovernanceTable.ALIAS);
                        participants.add(new GovernedParticipant(guid, alias));
                    }
                }
            }
        } catch (SQLException e) {
            throw new DaoException("Could not find governed participants for " + operatorGuid, e);
        }
        return participants;
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

    /**
     * Returns the internal ddp user guid for the given auth0 user id.
     */
    public String queryDdpUserGuidFromAuth0Id(Handle handle, String auth0Domain, String auth0UserId) throws
            SQLException {
        String ddpUserId;
        try (PreparedStatement stmt = handle.getConnection().prepareStatement(userGuidForAuth0IdQuery)) {
            stmt.setString(1, auth0UserId);
            stmt.setString(2, auth0Domain);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new DaoException("Could not find auth0 user id " + auth0UserId);
                } else {
                    ddpUserId = rs.getString(SqlConstants.DDP_USER_GUID);
                    if (rs.next()) {
                        throw new DaoException("Too many rows returned for " + auth0UserId);
                    }
                }
            }
        }
        return ddpUserId;
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

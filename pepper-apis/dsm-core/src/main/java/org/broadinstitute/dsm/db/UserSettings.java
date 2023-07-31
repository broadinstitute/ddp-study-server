package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.statics.QueryExtension;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class UserSettings {

    private static final Logger logger = LoggerFactory.getLogger(UserSettings.class);

    private static final String SQL_UPDATE_USER_SETTINGS =
            "UPDATE user_settings SET rows_on_page = ?, rows_set_0 = ?, rows_set_1 = ?, rows_set_2 = ?, date_format = ? WHERE user_id = ?";
    private static final String SQL_SELECT_USER_SETTINGS =
            "SELECT rows_on_page, rows_set_0, rows_set_1, rows_set_2, date_format FROM user_settings settings, access_user user "
                    + "WHERE user.user_id = settings.user_id AND user.is_active = 1";
    private static final String SQL_INSERT_USER_SETTINGS = "INSERT INTO user_settings SET user_id = ?";

    private static final String SQL_DELETE_USER_SETTINGS = "DELETE FROM user_settings WHERE user_id = ?";

    private static final String USER_ID = "userId";

    private int rowsOnPage;
    private int rowSet0;
    private int rowSet1;
    private int rowSet2;
    private String dateFormat;
    private String defaultTissueFilter;
    private String defaultParticipantFilter;

    public UserSettings(int rowsOnPage, int rowSet0, int rowSet1, int rowSet2, String dateFormat) {
        this.rowsOnPage = rowsOnPage;
        this.rowSet0 = rowSet0;
        this.rowSet1 = rowSet1;
        this.rowSet2 = rowSet2;
        this.dateFormat = dateFormat;
        this.defaultParticipantFilter = null;
        this.defaultTissueFilter = null;
    }

    public static void editUserSettings(@NonNull String userId, @NonNull UserSettings userSettings) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_UPDATE_USER_SETTINGS)) {
                stmt.setInt(1, userSettings.getRowsOnPage());
                stmt.setInt(2, userSettings.getRowSet0());
                stmt.setInt(3, userSettings.getRowSet1());
                stmt.setInt(4, userSettings.getRowSet2());
                stmt.setString(5, userSettings.getDateFormat());
                stmt.setString(6, userId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    logger.info("Updated user settings for user w/ userID " + userId);
                } else {
                    throw new RuntimeException(
                            "Error updating user settings for user w/ userID " + userId + " it was updating " + result + " rows");
                }
            } catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error updating user settings for user w/ userID " + userId, results.resultException);
        }
    }

    public static UserSettings getUserSettings(@NonNull String email) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_USER_SETTINGS + QueryExtension.BY_USER_EMAIL)) {
                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = new UserSettings(rs.getInt(DBConstants.ROWS_ON_PAGE),
                                rs.getInt(DBConstants.ROWS_SET_0),
                                rs.getInt(DBConstants.ROWS_SET_1),
                                rs.getInt(DBConstants.ROWS_SET_2),
                                rs.getString(DBConstants.DATE_FORMAT)
                        );
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });
        UserSettings us = (UserSettings) results.resultValue;
        if (us != null) {
            us.defaultTissueFilter = ViewFilter.getDefaultFilterForUser(email, "tissueList");
            us.defaultParticipantFilter = ViewFilter.getDefaultFilterForUser(email, "participantList");
        }
        logger.info("UserSettings for user w/ email " + email);
        return us;
    }

    public static int createUserSettings(int userId) {
        return inTransaction(conn -> insertUserSetting(conn, userId));
    }

    public static int insertUserSetting(Connection conn, int userId) {
        int id = -1;
        try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_USER_SETTINGS, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, userId);
            int result = stmt.executeUpdate();
            if (result != 1) {
                throw new DsmInternalError("Error inserting user setting. Result count was " + result);
            }
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    id = rs.getInt(1);
                }
            }
        } catch (SQLException ex) {
            throw new DsmInternalError("Error inserting user setting", ex);
        }
        return id;
    }

    public static int deleteUserSettings(int userId) {
        return inTransaction(conn -> {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_DELETE_USER_SETTINGS)) {
                stmt.setInt(1, userId);
                return stmt.executeUpdate();
            } catch (SQLException ex) {
                String msg = String.format("Error deleting user settings: userId=%d", userId);
                throw new DsmInternalError(msg, ex);
            }
        });
    }
}

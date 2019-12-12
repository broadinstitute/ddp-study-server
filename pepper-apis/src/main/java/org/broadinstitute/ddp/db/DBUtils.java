package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.SqlConstants.UserTable;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;

public final class DBUtils {

    /**
     * Client-provided stable codes must adhere to this pattern with one or more of the following characters: lowercase alpha, uppercase
     * alpha, numeric digit, underscore, and dash. Coincidentally, this is also what FireCloud allows as the column names.
     */
    public static final String STABLE_CODE_PATTERN = "[a-zA-Z0-9_\\-]+";

    private static final String IS_GUID_UNIQUE_QUERY_TEMPLATE = "select 1 from %s where %s = ?";

    /**
     * Load necessary sql commands from given config for all daos.
     *
     * @param sqlConfig the config object with sql commands
     */
    public static void loadDaoSqlCommands(Config sqlConfig) {
        ActivityInstanceDao.loadSqlCommands(sqlConfig);
        StudyActivityDao.loadSqlCommands(sqlConfig);
        ConsentElectionDao.loadSqlCommands(sqlConfig);
    }

    public static boolean matchesCodePattern(String stableCode) {
        return stableCode != null && stableCode.matches(STABLE_CODE_PATTERN);
    }

    /**
     * Check expected result against the actual result. Useful for checking the right amount of rows were affected.
     *
     * @param messageFormat string to format for use when throwing an exception, will be given "expected" and "actual" values as arguments
     * @param expected      the expected value
     * @param actual        the actual value
     * @param <T>           the type of value to check
     * @return the actual value if matches
     * @throws DaoException if values don't match
     */
    public static <T> T checkResult(String messageFormat, T expected, T actual) {
        if (expected.equals(actual)) {
            return actual;
        } else {
            throw new DaoException(String.format(messageFormat, expected, actual));
        }
    }

    public static int checkInsert(int expected, int actual) {
        return checkResult("Expected to insert %1$d rows but did %2$d", expected, actual);
    }

    public static int checkUpdate(int expected, int actual) {
        return checkResult("Expected to update %1$d rows but did %2$d", expected, actual);
    }

    public static int checkDelete(int expected, int actual) {
        return checkResult("Expected to delete %1$d rows but did %2$d", expected, actual);
    }

    /**
     * Generate a unique user guid.
     *
     * @param handle the database handle
     * @return unique guid
     */
    public static String uniqueUserGuid(Handle handle) {
        return uniqueGuid(GuidUtils::randomUserGuid, handle, UserTable._NAME, UserTable.GUID);
    }

    public static String uniqueUserHruid(Handle handle) {
        return uniqueGuid(GuidUtils::randomUserHruid, handle, UserTable._NAME, UserTable.HRUID);
    }

    /**
     * Generate a standard guid that is unique for given table.
     *
     * @param handle     the database handle
     * @param table      the table of interest
     * @param guidColumn the column that holds guids
     * @return unique guid
     */
    public static String uniqueStandardGuid(Handle handle, String table, String guidColumn) {
        return uniqueGuid(GuidUtils::randomStandardGuid, handle, table, guidColumn);
    }

    /**
     * Helper to check uniqueness of generated guids against database table.
     */
    private static String uniqueGuid(Supplier<String> generator, Handle handle, String table, String guidColumn) {
        String query = String.format(IS_GUID_UNIQUE_QUERY_TEMPLATE, table, guidColumn);
        String candidate = null;
        boolean unique = false;

        while (!unique) {
            candidate = generator.get();
            try (PreparedStatement stmt = handle.getConnection().prepareStatement(query)) {
                stmt.setString(1, candidate);
                ResultSet rs = stmt.executeQuery();
                unique = !rs.next();
            } catch (SQLException e) {
                String msg = "Cannot determine uniqueness of generated guid for table "
                        + table + " column " + guidColumn;
                throw new RuntimeException(msg, e);
            }
        }

        return candidate;
    }
}

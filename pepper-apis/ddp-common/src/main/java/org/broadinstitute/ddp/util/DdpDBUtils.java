package org.broadinstitute.ddp.util;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;

import org.broadinstitute.ddp.db.SqlConstants;
import org.jdbi.v3.core.Handle;

public class DdpDBUtils {
    /**
     * Client-provided stable codes must adhere to this pattern with one or more of the following characters: lowercase alpha, uppercase
     * alpha, numeric digit, underscore, and dash. Coincidentally, this is also what FireCloud allows as the column names.
     */
    public static final String STABLE_CODE_PATTERN = "[a-zA-Z0-9_\\-]+";


    private static final String IS_GUID_UNIQUE_QUERY_TEMPLATE = "select 1 from %s where %s = ?";

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

    /**
     * Generate a unique user guid.
     *
     * @param handle the database handle
     * @return unique guid
     */
    public static String uniqueUserGuid(Handle handle) {
        return uniqueGuid(GuidUtils::randomUserGuid, handle, SqlConstants.UserTable._NAME, SqlConstants.UserTable.GUID);
    }

    public static String uniqueUserHruid(Handle handle) {
        return uniqueGuid(GuidUtils::randomUserHruid, handle, SqlConstants.UserTable._NAME, SqlConstants.UserTable.HRUID);
    }
}

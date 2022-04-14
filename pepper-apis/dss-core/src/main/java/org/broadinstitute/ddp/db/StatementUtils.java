package org.broadinstitute.ddp.db;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

public class StatementUtils {

    /**
     * Given a prepared statement, set value of boxed type Integer.
     *
     * @param stmt              prepared statement
     * @param parameterPosition location of Integer in statement
     * @param value             value of Integer
     */
    static void setInteger(PreparedStatement stmt, int parameterPosition, Integer value) throws SQLException {
        if (value != null) {
            stmt.setInt(parameterPosition, value);
        } else {
            stmt.setNull(parameterPosition, Types.INTEGER);
        }
    }

    /**
     * Given a prepared statement, set value of boxed type Long.
     *
     * @param stmt              prepared statement
     * @param parameterPosition location of Long in statement
     * @param value             value of Long
     */
    static void setLong(PreparedStatement stmt, int parameterPosition, Long value) throws SQLException {
        if (value != null) {
            stmt.setLong(parameterPosition, value);
        } else {
            stmt.setNull(parameterPosition, Types.BIGINT);
        }
    }

    /**
     * Given a prepared statement, set value of boxed type String.
     *
     * @param stmt              prepared statement
     * @param parameterPosition location of String in statement
     * @param value             value of String
     */
    public static void setString(PreparedStatement stmt, int parameterPosition, String value) throws SQLException {
        if (value != null) {
            stmt.setString(parameterPosition, value);
        } else {
            stmt.setNull(parameterPosition, Types.VARCHAR);
        }
    }

}

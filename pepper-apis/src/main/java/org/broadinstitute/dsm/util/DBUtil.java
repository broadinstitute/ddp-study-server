package org.broadinstitute.dsm.util;

import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class DBUtil {

    private static final String SQL_SELECT_BOOKMARK = "SELECT value FROM bookmark WHERE instance = ?";
    private static final String SQL_UPDATE_BOOKMARK = "UPDATE bookmark SET value = ? WHERE instance = ?";

    public static long getBookmark(String bookmarkName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try {
                dbVals.resultValue = getBookmark(conn, bookmarkName);
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting bookmark " + bookmarkName, results.resultException);
        }
        return (Long) results.resultValue;
    }

    public static Long getBookmark(Connection conn, String bookmarkName) {
        if (conn != null) {
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_BOOKMARK,ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
                stmt.setString(1, bookmarkName);
                try (ResultSet rs = stmt.executeQuery()) {
                    rs.last();
                    int count = rs.getRow();
                    rs.beforeFirst();
                    if (count == 1 && rs.next()) {
                        return rs.getLong(DBConstants.VALUE);
                    }
                    throw new RuntimeException("Error getting bookmark " + bookmarkName);
                }
            }
            catch (Exception ex) {
                throw new RuntimeException("Error getting bookmark " + bookmarkName, ex);
            }
        }
        return null;
    }

    public static void updateBookmark(long value, String bookmarkName) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(-1);
            try {
                updateBookmark(conn, value, bookmarkName);
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting bookmark " + bookmarkName, results.resultException);
        }
    }

    public static void updateBookmark(Connection conn, long value, String bookmarkName) {//writeBookmarkIntoDb
        if (conn != null) {
            try (PreparedStatement updateBookmark = conn.prepareStatement(SQL_UPDATE_BOOKMARK)) {
                updateBookmark.setLong(1, value);
                updateBookmark.setString(2, bookmarkName);
                int result = updateBookmark.executeUpdate();
                if (result != 1) {
                    throw new RuntimeException("Error updating bookmark " + bookmarkName);
                }
            }
            catch (SQLException e) {
                throw new RuntimeException("Error updating bookmark " + bookmarkName, e);
            }
        }
    }

    public static String getFinalQuery (@NonNull String query, String additionalQuery) {
        if (StringUtils.isNotBlank(additionalQuery)) {
            query = query.concat(additionalQuery);
        }
        return query;
    }

    public static boolean existsExternalOrderNumber(String externalOrderNumber){
        String query = "Select * from ddp_kit_request where external_order_number = ?";
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement selectKitRequest = conn.prepareStatement(query)) {
                selectKitRequest.setString(1, externalOrderNumber);
                try (ResultSet rs = selectKitRequest.executeQuery();) {
                    if (rs.next()) {
                        dbVals.resultValue = true;
                    }else{
                        dbVals.resultValue = false;
                    }

                }
                catch (Exception e) {
                    throw new RuntimeException("Error getting values from db", e);
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error checking if values exist in db", results.resultException);
        }
        return (boolean) results.resultValue;
    }
}

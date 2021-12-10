package org.broadinstitute.dsm.util.tools.util;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

public class DBUtil {

    private static final Logger logger = LoggerFactory.getLogger(DBUtil.class);

    public static final String GET_REALM_QUERY = "SELECT * FROM ddp_instance WHERE instance_name = ?";

    public static final String SM_ID = "SM-ID";
    public static final String RECEIVED = "Received Date";

    public static Long getLong(String dateString) {
        if (StringUtils.isNotBlank(dateString) && !"#N/A".equals(dateString)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy");
                Date date = sdf.parse(dateString);
                return date.getTime();
            }
            catch (Exception e) {
                logger.error("Failed to convert string to milliseconds");
            }
        }
        return null;
    }

    public static String changeDateFormat(String dateString) {
        if (StringUtils.isNotBlank(dateString)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yy");
                Date date = sdf.parse(dateString);
                SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd");
                return sdf2.format(date);
            }
            catch (Exception e) {
                logger.error("Failed to change format of date " + dateString);
            }
        }
        return null;
    }

    public static String checkNotReceived(Connection conn, String selectQuery, Object kitInfo, String returnColumn) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(selectQuery,ResultSet.TYPE_SCROLL_SENSITIVE,ResultSet.CONCUR_READ_ONLY)) {
            stmt.setObject(1, kitInfo);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.last();
                int count = rs.getRow();
                rs.beforeFirst();
                if (count < 2) { // 0 rows are ok, more than 1 row is not good!
                    if (rs.next()) {
                        return rs.getString(returnColumn);
                    }
                }
                else {
                    throw new RuntimeException("Got more than 1 row back. Rowcount: " + count);
                }
            }
        }
        return null;
    }

    public static void setToReceived(Connection conn, String updateQuery, Object kitInfo, Long receivedDate) throws Exception {
        try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            stmt.setLong(1, receivedDate);
            stmt.setObject(2, kitInfo);
            int rows = stmt.executeUpdate();
            if (rows > 2) {
                throw new RuntimeException("Updated " + rows + " rows");
            }
            else if (rows == 0) {
                logger.warn("Kit w/ SM-ID " + kitInfo + " not found");
            }
            else if (rows == 1) {
                logger.info("Kit w/ SM-ID " + kitInfo + " updated");
            }
        }
    }
}

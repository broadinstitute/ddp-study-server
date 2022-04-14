package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cancer {

    public static final String SQL_GET_ALL_ACTIVE_CANCERS = "SELECT display_name FROM cancer_list WHERE active=1";
    private static final Logger logger = LoggerFactory.getLogger(Cancer.class);
    private String displayName;
    private int active;
    private int id;

    public static List<String> getCancers() {
        List<String> cancerList = new ArrayList<>();

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();

            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_ALL_ACTIVE_CANCERS)) {
                if (stmt != null) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            cancerList.add(rs.getString(DBConstants.DISPLAY_NAME));
                        }
                    }
                } else {
                    throw new RuntimeException("Cancer list is empty");
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting cancer list ", results.resultException);
        }
        logger.info("cancer list size is " + cancerList.size() + ".");

        return cancerList;

    }
}

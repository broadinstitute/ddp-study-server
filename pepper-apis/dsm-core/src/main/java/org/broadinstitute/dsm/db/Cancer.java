package org.broadinstitute.dsm.db;

import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class Cancer {

    private static final Logger logger = LoggerFactory.getLogger(Cancer.class);

    public static final String SQL_GET_ALL_ACTIVE_CANCERS = "SELECT display_name FROM cancer_list WHERE active=1";

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
                }
                else {
                    throw new RuntimeException("Cancer list is empty");
                }
            }
            catch (SQLException ex) {
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

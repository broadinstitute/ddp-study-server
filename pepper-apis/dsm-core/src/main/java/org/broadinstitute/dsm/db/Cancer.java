package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.broadinstitute.ddp.db.dto.CancerItem;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Cancer {

    public static final String SQL_GET_ALL_ACTIVE_CANCERS = "SELECT display_name, iso_language_code FROM cancer_list WHERE active=1";

    private static final Logger logger = LoggerFactory.getLogger(Cancer.class);

    public static List<CancerItem> getCancers() {
        List<CancerItem> cancerList = new ArrayList<>();

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();

            try (PreparedStatement stmt = conn.prepareStatement(SQL_GET_ALL_ACTIVE_CANCERS)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        cancerList.add(new CancerItem(rs.getString(DBConstants.DISPLAY_NAME),
                                rs.getString(DBConstants.LANGUAGE)));
                    }
                }
            } catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DsmInternalError("Error getting cancer list ", results.resultException);
        }
        logger.info("returning cancer list with " + cancerList.size() + " items.");

        return cancerList;

    }
}

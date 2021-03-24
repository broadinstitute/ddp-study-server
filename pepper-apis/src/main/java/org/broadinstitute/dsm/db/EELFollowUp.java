package org.broadinstitute.dsm.db;

import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class EELFollowUp {

    private static final Logger logger = LoggerFactory.getLogger(EELFollowUp.class);

    private static final String SQL_SELECT_FOLLOW_UP = "SELECT SGE_ID, follow_up FROM eel_follow_up WHERE ddp_instance_id = (SELECT ddp_instance_id FROM ddp_instance WHERE instance_name = ?)";

    private String sgeId;
    private String followUp;

    public EELFollowUp(String sgeId, String followUp) {
        this.sgeId = sgeId;
        this.followUp = followUp;
    }

    /**
     * Get followUp dates for given source
     * @param source instance_name (name of the instance. In table ddp_instance)
     * @return Map<String, EELFollowUp>
     *     Key: (String) SGE_ID sendGridEvent id
     *     Value: EELFollowUp
     */
    public static Map<String, EELFollowUp> getFollowUpDate(@NonNull String source) {
        Map<String, EELFollowUp> eelFollowUpMap = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_FOLLOW_UP)) {
                stmt.setString(1, source);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String sgeId = rs.getString(DBConstants.SGE_ID);
                        String followUpDate = rs.getString(DBConstants.FOLLOW_UP);
                        eelFollowUpMap.put(sgeId, new EELFollowUp(sgeId, followUpDate));
                    }
                }
            }
            catch (SQLException e) {
                dbVals.resultException = e;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting data from eel db ", results.resultException);
        }
        logger.info("Found " + eelFollowUpMap.size() + " eel follow-up data for source " + source);
        return eelFollowUpMap;
    }
}

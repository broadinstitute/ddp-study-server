package org.broadinstitute.dsm.db;

import lombok.Getter;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.dsm.statics.DBConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Getter
public class SurveyTrigger {

    private static final Logger logger = LoggerFactory.getLogger(SurveyTrigger.class);

    private static final String SQL_SELECT_SURVEY_TRIGGER = "SELECT st.survey_trigger_id, st.note, st.created_date, st.created_by, user.name FROM ddp_survey_trigger st, access_user user WHERE st.created_by = user.user_id";

    private String surveyTriggerId;
    private String reason;
    private String user;
    private long triggeredDate;

    public SurveyTrigger(String surveyTriggerId, String reason, String user, long triggeredDate) {
        this.surveyTriggerId = surveyTriggerId;
        this.reason = reason;
        this.user = user;
        this.triggeredDate = triggeredDate;
    }

    public static Map<String, SurveyTrigger> getSurveyTriggers() {
        Map<String, SurveyTrigger> surveyTriggers = new HashMap<>();
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_SURVEY_TRIGGER)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString(DBConstants.SURVEY_TRIGGER_ID);
                        surveyTriggers.put(id, new SurveyTrigger(id,
                                rs.getString(DBConstants.NOTE),
                                rs.getString(DBConstants.NAME),
                                rs.getLong(DBConstants.CREATED_DATE)));
                    }
                }
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting list of survey triggers ", results.resultException);
        }
        logger.info("Found " + surveyTriggers.size() + " survey triggers ");
        return surveyTriggers;
    }
}

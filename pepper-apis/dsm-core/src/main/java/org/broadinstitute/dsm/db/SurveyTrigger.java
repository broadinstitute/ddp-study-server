package org.broadinstitute.dsm.db;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import lombok.Getter;
import lombok.NonNull;
import org.broadinstitute.dsm.exception.DsmInternalError;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.broadinstitute.dsm.util.DSMConfig;
import org.broadinstitute.lddp.db.SimpleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class SurveyTrigger {

    private static final Logger logger = LoggerFactory.getLogger(SurveyTrigger.class);

    private static final String SQL_SELECT_SURVEY_TRIGGER =
            "SELECT st.survey_trigger_id, st.note, st.created_date, st.created_by, user.name "
                    + "FROM ddp_survey_trigger st, access_user user WHERE st.created_by = user.user_id";

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
            } catch (Exception ex) {
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

    public static long insertTrigger(@NonNull String userId, @NonNull String reason, long currentTime) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(
                    DSMConfig.getSqlFromConfig(ApplicationConfigConstants.INSERT_SURVEY_TRIGGER), Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, reason);
                stmt.setLong(2, currentTime);
                stmt.setString(3, userId);
                int result = stmt.executeUpdate();
                if (result == 1) {
                    try (ResultSet rs = stmt.getGeneratedKeys()) {
                        if (rs.next()) {
                            long surveyTriggerId = rs.getLong(1);
                            dbVals.resultValue = surveyTriggerId;
                        }
                    } catch (Exception e) {
                        throw new DsmInternalError("Error getting id of new survey trigger reason ", e);
                    }
                } else {
                    throw new DsmInternalError("Something went wrong entering survey trigger reason into db");
                }
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Couldn't enter survey trigger reason into db ", results.resultException);
        }
        return (long) results.resultValue;
    }
}

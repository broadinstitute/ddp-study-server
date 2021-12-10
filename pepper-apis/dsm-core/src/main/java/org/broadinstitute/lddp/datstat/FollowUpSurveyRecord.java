package org.broadinstitute.lddp.datstat;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.email.Recipient;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.handlers.util.ParticipantSurveyInfo;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class FollowUpSurveyRecord
{
    private static final Logger logger = LoggerFactory.getLogger(FollowUpSurveyRecord.class);

    private static final String LOG_PREFIX = "FOLLOW-UP QUEUE - ";
    private static final int SEC_IN_HOUR = 3600;

    private Long recordId;
    private String participantId;
    private String survey;
    private String followUpInstance;
    private Recipient recipient;
    private Long triggerId;

    public static final String ERROR_SURVEY_RECORD_ADD = "An error occurred adding survey records.";
    public static final String ERROR_SURVEY_RECORD_DELETE_UNPROCESSED = "An error occurred deleting unprocessed records.";
    public static final String ERROR_SURVEY_RECORD_UPDATE = "An error occurred updating survey records.";
    public static final String ERROR_SURVEY_RECORD_QUERY = "An error occurred while querying for survey records.";
    public static final String ERROR_SINGLE_SURVEY_RECORD_QUERY = "An error occurred while querying for a single survey record.";
    public static final String NONREPEATING_SURVEY_INSTANCE = "NOREPEAT";

    public static final String SQL_PARTIALLY_PROCESSED_IN_QUEUE = "SELECT COUNT(*) FROM FOLLOWUP_SURVEY_QUEUE WHERE FSRVY_DATE_PROCESSED = -1";

    public static final String SQL_TOTAL_RECORDS_IN_QUEUE = "SELECT COUNT(*) FROM FOLLOWUP_SURVEY_QUEUE";

    private static final String SQL_INSERT_SURVEY_RECORD = "INSERT INTO FOLLOWUP_SURVEY_QUEUE (FSRVY_DATE_CREATED, " +
            "FSRVY_RECORD_ID, FSRVY_SURVEY, FSRVY_SURVEY_INSTANCE, DSM_TRIGGER_ID%s%s) VALUES (?, ?, ?, ?, ?%s%s)";

    private static final String SQL_RECORDS_TO_PROCESS = "SELECT FSRVY_ID, FSRVY_RECORD_ID, FSRVY_SURVEY, FSRVY_SURVEY_INSTANCE, DSM_TRIGGER_ID%s FROM FOLLOWUP_SURVEY_QUEUE " +
            "WHERE FSRVY_DATE_PROCESSED IS NULL ORDER BY FSRVY_ID";

    private static final String SQL_RECORDS_FOR_SURVEY = "SELECT FSRVY_RECORD_ID, FSRVY_SURVEY, FSRVY_SURVEY_INSTANCE, FSRVY_DATE_CREATED, DSM_TRIGGER_ID FROM FOLLOWUP_SURVEY_QUEUE " +
            "WHERE FSRVY_SURVEY = ? ORDER BY FSRVY_ID";

    private static final String SQL_RECORD_FOR_SINGLE_SURVEY = "SELECT FSRVY_ID, FSRVY_RECORD_ID, FSRVY_SURVEY, FSRVY_SURVEY_INSTANCE, DSM_TRIGGER_ID, EMAIL_DATA FROM FOLLOWUP_SURVEY_QUEUE " +
            "WHERE FSRVY_SURVEY = ? AND FSRVY_RECORD_ID = ? AND FSRVY_SURVEY_INSTANCE = ?";

    private static final String SQL_DELETE_ALL_UNPROCESSED_SURVEYS = "DELETE FROM FOLLOWUP_SURVEY_QUEUE WHERE FSRVY_RECORD_ID = ? AND FSRVY_DATE_PROCESSED IS NULL";

    private static final String SQL_UPDATE_PROCESSED_RECORDS = "UPDATE FOLLOWUP_SURVEY_QUEUE " +
            "SET FSRVY_DATE_PROCESSED = ? WHERE FSRVY_ID IN (X)";

    private static final String SQL_RESET_PROCESSED_RECORD = "UPDATE FOLLOWUP_SURVEY_QUEUE " +
            "SET FSRVY_DATE_PROCESSED = null WHERE FSRVY_ID = ?";

    public FollowUpSurveyRecord(@NonNull Long recordId, @NonNull String participantId, @NonNull String survey, @NonNull String followUpInstance, @NonNull Long triggerId,
                                Recipient recipient) {
        this.recordId = recordId;
        this.participantId = participantId;
        this.survey = survey;
        this.followUpInstance = followUpInstance;
        this.triggerId = triggerId;
        this.recipient = recipient;
    }

    public static void add(@NonNull String participantId, @NonNull SurveyConfig surveyConfig, @NonNull Long dsmTriggerId) {
        Recipient recipient = new Recipient();
        recipient.setId(participantId);
        add(recipient, surveyConfig, true, dsmTriggerId);
    }

    public static void add(@NonNull Recipient recipient, @NonNull SurveyConfig surveyConfig, boolean datStatHasParticipantLists,
                           @NonNull Long dsmTriggerId) {
        add(recipient, surveyConfig, datStatHasParticipantLists, dsmTriggerId, false);
    }

    public static void add(@NonNull Recipient recipient, @NonNull SurveyConfig surveyConfig, boolean datStatHasParticipantLists,
                           @NonNull Long dsmTriggerId, boolean addNow) {
        logger.info(LOG_PREFIX + "Adding survey record...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(String.format(SQL_INSERT_SURVEY_RECORD,
                    ((datStatHasParticipantLists) ? "" : ", EMAIL_DATA"),
                    ((!addNow) ? "" : ", FSRVY_DATE_PROCESSED"),
                    ((datStatHasParticipantLists) ? "" : ", ?"),
                    ((!addNow) ? "" : ", -1")))) {
                stmt.setLong(1, Utility.getCurrentEpoch());
                stmt.setString(2, recipient.getId());
                stmt.setString(3, surveyConfig.getSurveyPathName());
                stmt.setString(4, (surveyConfig.getFollowUpType() == SurveyConfig.FollowUpType.NONREPEATING) ?
                        NONREPEATING_SURVEY_INSTANCE: java.util.UUID.randomUUID().toString());
                stmt.setLong(5, dsmTriggerId);
                if (!datStatHasParticipantLists) {
                    stmt.setString(6, new Gson().toJson(recipient));
                }

                dbVals.resultValue = stmt.executeUpdate();
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if ((results.resultException != null)||((Integer)results.resultValue != 1)) {
            throw new DMLException(ERROR_SURVEY_RECORD_ADD, results.resultException);
        }
    }

    public static void removeUnprocessedSurveys(@NonNull String participantId)
    {
        logger.info(LOG_PREFIX + "Removing unprocessed surveys...");
        Utility.removedUnprocessedFromQueue(participantId, SQL_DELETE_ALL_UNPROCESSED_SURVEYS, ERROR_SURVEY_RECORD_DELETE_UNPROCESSED);
    }

    public static void completeProcessing(@NonNull Long queueId) {
        logger.info(LOG_PREFIX + "Update record for processing completion...");
        Utility.updateProcessedInQueue(queueId, SQL_UPDATE_PROCESSED_RECORDS, ERROR_SURVEY_RECORD_UPDATE, Utility.QueueStatus.PROCESSED);
    }

    public static void resetProcessedSurvey(@NonNull Long queueId) {
        logger.info(LOG_PREFIX + "Resetting processed survey...");
        Utility.updateProcessedInQueue(queueId, SQL_UPDATE_PROCESSED_RECORDS, ERROR_SURVEY_RECORD_UPDATE, Utility.QueueStatus.UNPROCESSED);
    }

    public static ArrayList<FollowUpSurveyRecord> getRecordsForProcessing() {
        return getRecordsForProcessing(true);
    }

    public static ArrayList<FollowUpSurveyRecord> getRecordsForProcessing(boolean datStatHasParticipantLists) {
        SimpleResult results = inTransaction((conn) -> {
            ArrayList<FollowUpSurveyRecord> records = new ArrayList();
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(String.format(SQL_RECORDS_TO_PROCESS, ((datStatHasParticipantLists) ? "" : ", EMAIL_DATA")))) {
                logger.info(LOG_PREFIX + "About to execute query for unprocessed records.");
                try (ResultSet rs = stmt.executeQuery()) {
                    logger.info(LOG_PREFIX + "Unprocessed records query executed.");
                    while (rs.next()) {
                        records.add(new FollowUpSurveyRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                rs.getLong(5), (datStatHasParticipantLists) ? null : (new Gson().fromJson(rs.getString(6), Recipient.class))));
                    }
                }
                dbVals.resultValue = records;
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(ERROR_SURVEY_RECORD_QUERY, results.resultException);
        }

        return (ArrayList<FollowUpSurveyRecord>)results.resultValue;
    }

    public static Map<String, ParticipantSurveyInfo> getParticipantSurveyInfo(@NonNull String survey) {
        SimpleResult results = inTransaction((conn) -> {
            Map<String, ParticipantSurveyInfo> records = new HashMap<>();
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_RECORDS_FOR_SURVEY)) {
                stmt.setString(1, survey);
                logger.info(LOG_PREFIX + "About to execute query for all " + survey + " records.");
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        records.put(generateAltPidInstanceKey(rs.getString(1), rs.getString(3)),
                                new ParticipantSurveyInfo(rs.getString(1), rs.getString(2), rs.getString(3),
                                        rs.getLong(4), rs.getLong(5)));
                    }
                }
                dbVals.resultValue = records;
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(ERROR_SURVEY_RECORD_QUERY, results.resultException);
        }

        return (Map<String, ParticipantSurveyInfo>)results.resultValue;
    }

    public static String generateAltPidInstanceKey(@NonNull String altPid, @NonNull String followUpInstance) {
        return altPid + "-" + followUpInstance;
    }

    public static long getCountInQueue() {
        return Utility.getCountInBigTable(SQL_TOTAL_RECORDS_IN_QUEUE);
    }

    public static boolean queueCheck() {

        boolean ok = false;

        try {
            ok = (getCountOfPartiallyProcessed() == 0);
        }
        catch (Exception ex) {
            logger.error(LOG_PREFIX + "A problem occurred querying the DB.", ex);
        }

        return ok;
    }

    public static long getCountOfPartiallyProcessed() {
        return Utility.getCountInBigTable(SQL_PARTIALLY_PROCESSED_IN_QUEUE);
    }


    public static FollowUpSurveyRecord getRecord(@NonNull String survey, @NonNull String participantId, @NonNull String followUpInstance,
                                                 boolean datStatHasParticipantLists) {
        SimpleResult results = inTransaction((conn) -> {
            FollowUpSurveyRecord record = null;
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_RECORD_FOR_SINGLE_SURVEY)) {
                stmt.setString(1, survey);
                stmt.setString(2, participantId);
                stmt.setString(3, followUpInstance);
                logger.info(LOG_PREFIX + "About to execute query for single survey record.");
                try (ResultSet rs = stmt.executeQuery()) {
                    int i = 0;
                    while (rs.next()) {
                        if (i > 0) {
                            throw new DMLException("Too many matching follow-up surveys found for " +
                                    "survey = " + survey + "; participant = " + participantId + "; followup instance = " + followUpInstance);
                        }
                        else {
                            record = new FollowUpSurveyRecord(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                                    rs.getLong(5), (datStatHasParticipantLists) ? null : (new Gson().fromJson(rs.getString(6), Recipient.class)));
                        }
                    }
                }
                dbVals.resultValue = record;
            }
            catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(ERROR_SINGLE_SURVEY_RECORD_QUERY, results.resultException);
        }

        return (FollowUpSurveyRecord)results.resultValue;
    }
}
package org.broadinstitute.lddp.email;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.lddp.db.SimpleResult;
import org.broadinstitute.lddp.exception.DMLException;
import org.broadinstitute.lddp.util.Utility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Data
public class EmailRecord {
    public static final String ERROR_EMAIL_RECORD_ADD = "An error occurred adding email records.";
    public static final String ERROR_EMAIL_RECORD_DELETE_REMINDERS = "An error occurred deleting reminder email records.";
    public static final String ERROR_EMAIL_RECORD_UPDATE = "An error occurred updating email records.";
    public static final String ERROR_EMAIL_RECORD_QUERY = "An error occurred while querying for email records.";
    private static final Logger logger = LoggerFactory.getLogger(EmailRecord.class);
    private static final String LOG_PREFIX = "EMAIL QUEUE - ";
    private static final int SEC_IN_HOUR = 3600;
    private static final String SQL_INSERT_EMAIL_RECORD = "INSERT INTO EMAIL_QUEUE (EMAIL_DATE_CREATED, EMAIL_DATE_SCHEDULED, "
            + "REMINDER_TYPE, EMAIL_RECORD_ID, EMAIL_TEMPLATE, EMAIL_DATA) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String SQL_RECORDS_TO_PROCESS = "SELECT EMAIL_ID, EMAIL_TEMPLATE, EMAIL_DATA, REMINDER_TYPE FROM EMAIL_QUEUE "
            + "WHERE EMAIL_DATE_PROCESSED IS NULL AND EMAIL_DATE_SCHEDULED < ? ORDER BY EMAIL_DATE_SCHEDULED";
    private static final String SQL_DELETE_EMAIL_REMINDERS_EQUAL_TO_TYPE =
            "DELETE FROM EMAIL_QUEUE WHERE EMAIL_RECORD_ID = ? AND " + "REMINDER_TYPE = ? AND EMAIL_DATE_PROCESSED IS NULL";
    private static final String SQL_DELETE_EMAIL_REMINDERS_NOT_EQUAL_TO_TYPE = "DELETE FROM EMAIL_QUEUE WHERE EMAIL_RECORD_ID = ? AND "
            + "REMINDER_TYPE != ? AND EMAIL_DATE_PROCESSED IS NULL AND REMINDER_TYPE != 'NA'";
    private static final String SQL_DELETE_ALL_UNSENT_EMAILS =
            "DELETE FROM EMAIL_QUEUE WHERE EMAIL_RECORD_ID = ? AND " + "EMAIL_DATE_PROCESSED IS NULL";
    private static final String SQL_UPDATE_PROCESSED_RECORDS = "UPDATE EMAIL_QUEUE " + "SET EMAIL_DATE_PROCESSED = ? WHERE EMAIL_ID IN (X)";
    private static final String SQL_RESET_PROCESSED_RECORD = "UPDATE EMAIL_QUEUE " + "SET EMAIL_DATE_PROCESSED = null WHERE EMAIL_ID = ?";
    private Recipient recipient;
    private Long recordId; //EMAIL_ID PK value in DB table
    private String reminderType;

    public EmailRecord(@NonNull Recipient recipient, @NonNull Long recordId, @NonNull String reminderType) {
        this.recipient = recipient;
        this.recordId = recordId;
        this.reminderType = reminderType;
    }

    public static void add(String immediateEmailTemplate, @NonNull Recipient recipient, JsonElement reminderInfo,
                           @NonNull String emailGroupId) {
        add(immediateEmailTemplate, recipient, recipient.getCurrentStatus(), reminderInfo, emailGroupId);
    }

    private static void add(String immediateEmailTemplate, @NonNull Recipient recipient, String reminderType, JsonElement reminderInfo,
                            @NonNull String emailGroupId) {
        logger.info(LOG_PREFIX + "Adding email record(s)...");

        if ((immediateEmailTemplate == null) && (reminderInfo == null)) {
            throw new IllegalArgumentException("Immediate email and/or reminder email info required.");
        }

        String recipientJson = new Gson().toJson(recipient);

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_INSERT_EMAIL_RECORD)) {
                if (immediateEmailTemplate != null) {
                    long epochTime = Utility.getCurrentEpoch();
                    addRecord(stmt, epochTime, epochTime, "NA", emailGroupId, immediateEmailTemplate, recipient);
                }

                if (reminderInfo != null) {
                    addReminders(recipientJson, reminderType, reminderInfo, stmt, emailGroupId);
                }

                dbVals.resultValue = checkBatchCounts(stmt.executeBatch());
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if ((results.resultException != null) || (!(boolean) results.resultValue)) {
            throw new DMLException(ERROR_EMAIL_RECORD_ADD, results.resultException);
        }
    }

    public static void addOnlyReminders(@NonNull Recipient recipient, @NonNull JsonElement reminderInfo, @NonNull String emailGroupId)
            throws Exception {
        addOnlyReminders(recipient, recipient.getCurrentStatus(), reminderInfo, emailGroupId);
    }

    public static void addOnlyReminders(@NonNull Recipient recipient, @NonNull String reminderType, @NonNull JsonElement reminderInfo,
                                        @NonNull String emailGroupId) throws Exception {
        add(null, recipient, reminderType, reminderInfo, emailGroupId);
    }

    public static void removeOldReminders(@NonNull String emailGroupId, @NonNull String reminderType, boolean matchReminderType) {
        logger.info(LOG_PREFIX + "Removing old reminders...");

        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(
                    (matchReminderType) ? SQL_DELETE_EMAIL_REMINDERS_EQUAL_TO_TYPE : SQL_DELETE_EMAIL_REMINDERS_NOT_EQUAL_TO_TYPE)) {
                stmt.setString(1, emailGroupId);
                stmt.setString(2, reminderType);
                dbVals.resultValue = stmt.executeUpdate();
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(ERROR_EMAIL_RECORD_DELETE_REMINDERS, results.resultException);
        }
    }

    public static void startProcessing(@NonNull Long queueId) {
        logger.info(LOG_PREFIX + "Update record for initial processing...");
        Utility.updateProcessedInQueue(queueId, SQL_UPDATE_PROCESSED_RECORDS, ERROR_EMAIL_RECORD_UPDATE, Utility.QueueStatus.STARTED);
    }

    public static Map<String, ArrayList<EmailRecord>> getRecordsForProcessing() {
        SimpleResult results = inTransaction((conn) -> {
            Map<String, ArrayList<EmailRecord>> records = new HashMap<>();
            String template;
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_RECORDS_TO_PROCESS)) {
                stmt.setLong(1, Utility.getCurrentEpoch());
                logger.info(LOG_PREFIX + "About to execute query for unprocessed records.");
                try (ResultSet rs = stmt.executeQuery()) {
                    logger.info(LOG_PREFIX + "Unprocessed records query executed.");
                    while (rs.next()) {
                        template = rs.getString(2);
                        if (records.get(template) == null) {
                            records.put(template, new ArrayList<>());
                        }
                        records.get(template).add(new EmailRecord(new Gson().fromJson(rs.getString(3), Recipient.class), rs.getLong(1),
                                rs.getString(4)));

                    }
                }
                dbVals.resultValue = records;
            } catch (Exception ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new DMLException(ERROR_EMAIL_RECORD_QUERY, results.resultException);
        }

        return (Map<String, ArrayList<EmailRecord>>) results.resultValue;
    }

    public static int getRecordCount(Map<String, ArrayList<EmailRecord>> records) {
        int count = 0;
        for (ArrayList value : records.values()) {
            count = count + value.size();
        }
        return count;
    }

    private static void addRecord(@NonNull PreparedStatement stmt, @NonNull long created, @NonNull long scheduled, String reminderType,
                                  @NonNull String emailGroupId, @NonNull String template, @NonNull Recipient recipient) throws Exception {
        stmt.setLong(1, created);
        stmt.setLong(2, scheduled);
        stmt.setString(3, reminderType); //reminder type
        stmt.setString(4, emailGroupId); //could be an email address, altpid, whatever needs to be used to identify row or group of rows
        stmt.setString(5, template);
        stmt.setString(6, new Gson().toJson(recipient));
        stmt.addBatch();
    }

    private static boolean checkBatchCounts(int[] updateCounts) {
        try {
            for (int i = 0; i < updateCounts.length; i++) {
                if (updateCounts[i] != 1) {
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            logger.error(ERROR_EMAIL_RECORD_ADD, ex);
            return false;
        }
    }

    private static void addReminders(@NonNull String recipientJson, @NonNull String reminderType, @NonNull JsonElement reminderInfo,
                                     @NonNull PreparedStatement stmt, @NonNull String emailGroupId) throws Exception {
        JsonArray array = reminderInfo.getAsJsonArray();

        for (JsonElement reminder : array) {
            Recipient recipient = new Gson().fromJson(recipientJson, Recipient.class);

            long epochTime = Utility.getCurrentEpoch();
            addRecord(stmt, epochTime, epochTime + (reminder.getAsJsonObject().get("hours").getAsInt() * SEC_IN_HOUR), reminderType,
                    emailGroupId, reminder.getAsJsonObject().get("sendGridTemplate").getAsString(), recipient);
        }
    }
}

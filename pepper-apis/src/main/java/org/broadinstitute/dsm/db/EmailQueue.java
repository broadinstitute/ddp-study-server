package org.broadinstitute.dsm.db;

import com.google.gson.Gson;
import lombok.Data;
import lombok.NonNull;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.dsm.statics.DBConstants;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

@Data
public class EmailQueue {

    private static final String SQL_SELECT_REMINDERS_TO_PROCESS = "SELECT EMAIL_ID, EMAIL_DATA, EMAIL_DATE_CREATED, EMAIL_RECORD_ID, REMINDER_TYPE FROM EMAIL_QUEUE " +
            "WHERE EMAIL_DATE_PROCESSED IS NULL AND REMINDER_TYPE != 'NA' ORDER BY EMAIL_DATE_SCHEDULED";

    private Recipient recipient;
    private Long emailId; //EMAIL_ID PK value in DB table
    private Long reminderCreated;
    private String reminderType;
    private String recordId; //EMAIL_RECORD_ID

    public EmailQueue(@NonNull Recipient recipient, @NonNull Long emailId, @NonNull Long reminderCreated, @NonNull String reminderType, @NonNull String recordId) {
        this.recipient = recipient;
        this.emailId = emailId;
        this.reminderCreated = reminderCreated;
        this.reminderType = reminderType;
        this.recordId = recordId;
    }

    public static Map<String, List<EmailQueue>> getRemindersForProcessing() {
        SimpleResult results = inTransaction((conn) -> {
            Map<String, List<EmailQueue>> records = new HashMap<>();
            String reminder;
            SimpleResult dbVals = new SimpleResult(0);
            try (PreparedStatement stmt = conn.prepareStatement(SQL_SELECT_REMINDERS_TO_PROCESS)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        reminder = rs.getString(DBConstants.REMINDER_TYPE);
                        if (records.get(reminder) == null) {
                            records.put(reminder, new ArrayList<>());
                        }
                        records.get(reminder).add(new EmailQueue(new Gson().fromJson(rs.getString(DBConstants.EMAIL_DATA), Recipient.class),
                                rs.getLong(DBConstants.EMAIL_ID), rs.getLong(DBConstants.EMAIL_DATE_CREATED), rs.getString(DBConstants.REMINDER_TYPE),
                                rs.getString(DBConstants.EMAIL_RECORD_ID)));
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
            throw new RuntimeException("Failed to get queued reminders ", results.resultException);
        }

        return (Map<String, List<EmailQueue>>) results.resultValue;
    }
}

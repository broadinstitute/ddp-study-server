package org.broadinstitute.dsm.util;

import com.google.gson.*;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.db.SimpleResult;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.email.EmailClient;
import org.broadinstitute.ddp.email.EmailRecord;
import org.broadinstitute.ddp.email.Recipient;
import org.broadinstitute.ddp.exception.EmailQueueException;
import org.broadinstitute.dsm.db.EmailQueue;
import org.broadinstitute.dsm.db.MedicalRecord;
import org.broadinstitute.dsm.db.OncHistoryDetail;
import org.broadinstitute.dsm.db.Tissue;
import org.broadinstitute.dsm.route.AssignParticipantRoute;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;
import org.broadinstitute.dsm.statics.DBConstants;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import static org.broadinstitute.ddp.db.TransactionWrapper.inTransaction;

public class NotificationUtil {

    private static final Logger logger = LoggerFactory.getLogger(NotificationUtil.class);

    public static final String EMAIL_TYPE = "EXITED_KIT_RECEIVED_NOTIFICATION";
    public static final String UNIVERSAL_NOTIFICATION_TEMPLATE = "UNIVERSAL_NOTIFICATION_TEMPLATE";
    public static final String DSM_SUBJECT = "Study-Manager Notification";

    public static final String KITREQUEST_LINK = "/permalink/whereto?";

    private static String emailClassName = null;
    private static String emailKey = null;
    private static JsonObject emailClientSettings = null;
    private static Map<String, JsonElement> notificationLookup = new HashMap<>();
    private static Map<String, JsonElement> reminderNotificationLookup = new HashMap<>();

    public NotificationUtil(@NonNull Config config) {
        startup(config);
    }

    public synchronized void startup(@NonNull Config config) {
        emailClassName = config.getString(ApplicationConfigConstants.EMAIL_CLASS_NAME);
        emailKey = config.getString(ApplicationConfigConstants.EMAIL_KEY);
        emailClientSettings = (JsonObject) (new JsonParser().parse(config.getString(ApplicationConfigConstants.EMAIL_CLIENT_SETTINGS)));

        JsonArray array = (JsonArray) (new JsonParser().parse(config.getString(ApplicationConfigConstants.EMAIL_NOTIFICATIONS)));
        for (JsonElement notificationInfo : array) {
            notificationLookup.put(notificationInfo.getAsJsonObject().get(ApplicationConfigConstants.EMAIL_NOTIFICATION_REASON).getAsString(), notificationInfo);
        }
        array = (JsonArray) (new JsonParser().parse(config.getString(ApplicationConfigConstants.EMAIL_REMINDER_NOTIFICATIONS)));
        for (JsonElement reminderInfo : array) {
            if (reminderInfo.isJsonObject()) {
                reminderNotificationLookup.put(reminderInfo.getAsJsonObject().get(ApplicationConfigConstants.EMAIL_NOTIFICATION_REASON).getAsString(),
                        reminderInfo.getAsJsonObject().get(ApplicationConfigConstants.EMAIL_REMINDER_NOTIFICATIONS_REMINDERS).getAsJsonArray());
            }
        }
    }

    public JsonElement getPortalReminderNotifications(String id) {
        return reminderNotificationLookup.get(id);
    }

    public void sentNotification(String notificationRecipient, String message, String subject) {
        sentNotification(notificationRecipient, message, EMAIL_TYPE, subject);
    }

    public void sentNotification(String notificationRecipient, String message, String recordId, String subject) {
        try {
            if (StringUtils.isNotBlank(notificationRecipient)) {
                notificationRecipient = notificationRecipient.replaceAll("\\s", "");
                List<String> recipients = Arrays.asList(notificationRecipient.split(","));
                sentNotification(recipients, message, recordId, subject);
            }
        }
        catch (Exception e) {
            logger.error("Was not able to notify study staff.", e);
        }
    }

    public void sentNotification(List<String> recipients, String message, String recordId, String subject) {
        for (String recipient : recipients) {
            doNotification(recipient, message, recordId, subject);
        }
    }

    private void doNotification(@NonNull String recipient, String message, String recordId, String subject) {
        Map<String, String> mapy = new HashMap<>();
        mapy.put(":customText", message);
        mapy.put(":subject", subject);
        Recipient emailRecipient = new Recipient(recipient);
        if (EMAIL_TYPE.equals(recordId)) {
            emailRecipient.setUrl(TransactionWrapper.getSqlFromConfig(ApplicationConfigConstants.EMAIL_FRONTEND_URL_FOR_LINKS) + KITREQUEST_LINK);
        }
        emailRecipient.setSurveyLinks(mapy);
        queueCurrentAndFutureEmails(recordId, emailRecipient, recordId);
    }

    public void queueCurrentAndFutureEmails(@NonNull String reason, @NonNull Recipient recipient, @NonNull String recordId) {
        String email = null;

        try {
            email = recipient.getEmail();

            JsonElement notificationInfo = notificationLookup.get(reason);
            String template = notificationInfo.getAsJsonObject().get(ApplicationConfigConstants.EMAIL_NOTIFICATIONS_SEND_GRID_TEMPLATE_ID).getAsString();

            queueEmail(recipient, getPortalReminderNotifications(reason), template, recordId);
            EmailRecord.removeOldReminders(email, reason, false);
        }
        catch (Exception ex) {
            //NOTE: we need to swallow this because frontend should NOT get a 500 for this
            logger.error("Unable to queue email(s) for user with email = " + email + " and status = " + reason + ".", ex);
        }
    }

    private void queueEmail(@NonNull Recipient recipient, JsonElement reminderInfo, @NonNull String template, @NonNull String recordId) {
        try {
            EmailRecord.add(template, recipient, reminderInfo, recordId);
        }
        catch (Exception ex) {
            throw new RuntimeException("Unable to queue email for " + recipient.getEmail() + ".", ex);
        }
    }


    public void queueFutureEmails(@NonNull String reason, @NonNull Recipient recipient, @NonNull String recordId) {
        String email = null;

        try {
            email = recipient.getEmail();

            logger.info(reason);
            JsonElement reminderInfo = reminderNotificationLookup.get(reason);

            queueOnlyReminderEmail(recipient, reminderInfo, recordId);
            EmailRecord.removeOldReminders(recordId, reason, false);
        }
        catch (Exception ex) {
            //NOTE: we need to swallow this because frontend should NOT get a 500 for this
            logger.error("Unable to queue email(s) for user with email = " + email + " and status = " + reason + ".", ex);
        }
    }

    private void queueOnlyReminderEmail(@NonNull Recipient recipient, @NonNull JsonElement reminderInfo, @NonNull String recordId) {
        try {
            EmailRecord.addOnlyReminders(recipient, reminderInfo, recordId);
        }
        catch (Exception ex) {
            throw new RuntimeException("Unable to queue email for " + recipient.getEmail() + ".", ex);
        }
    }

    public void removeOldNotifications(@NonNull String reason, @NonNull String recordId) {
        EmailRecord.removeOldReminders(recordId, reason, false);
    }

    public void sentAbstractionExpertQuestion(@NonNull String from, @NonNull String name, @NonNull String to, String field, String question, @NonNull String sendGridTemplate) {
        Map<String, String> mapy = new HashMap<>();
        mapy.put(":customSubject", "Question about: " + field);
        mapy.put(":customText", question);
        mapy.put(":customSignature", name);
        Recipient emailRecipient = new Recipient(to);
        emailRecipient.setSurveyLinks(mapy);
        try {
            EmailClient abstractionEmailClient = (EmailClient) Class.forName(emailClassName).newInstance();
            JSONObject emailClientSettings = new JSONObject().put("sendGridFrom", from).put("sendGridFromName", name);
            abstractionEmailClient.configure(emailKey, new Gson().fromJson(emailClientSettings.toString(), JsonObject.class), "", null, "");
            abstractionEmailClient.sendSingleEmail(sendGridTemplate, emailRecipient, null);
        }
        catch (Exception ex) {
            logger.error("An error occurred trying to send abstraction expert question.", ex);
        }
    }


    public int sendQueuedNotifications() {
        logger.info("Checking for queued notifications...");

        Boolean success = true;
        int totalNotificationsSent = 0;
        int totalNotificationsToProcess = 0;

        try {
            EmailClient emailClient = (EmailClient) Class.forName(emailClassName).newInstance();
            emailClient.configure(emailKey, emailClientSettings, "", null, "");

            Map<String, ArrayList<EmailRecord>> records = EmailRecord.getRecordsForProcessing();

            totalNotificationsToProcess = EmailRecord.getRecordCount(records);

            if (totalNotificationsToProcess > 0) {
                //loop through the different templates
                for (Map.Entry<String, ArrayList<EmailRecord>> templateRecords : records.entrySet()) {
                    String template = "";
                    try {
                        template = templateRecords.getKey();

                        for (EmailRecord record : templateRecords.getValue()) {
                            emailClient.sendSingleEmail(template, record.getRecipient(), null);
                            EmailRecord.startProcessing(record.getRecordId());
                        }

                        totalNotificationsSent = totalNotificationsSent + templateRecords.getValue().size();
                    }
                    catch (Exception ex) {
                        success = false;
                        //for now swallow and log errors for individual templates so we can continue the loop
                        logger.error("Unable to process notifications for template " + template + ".", ex);
                    }
                }
            }

            //if any of the above failed let's throw an exception now...
            if (!success) {
                throw new EmailQueueException("An error occurred trying to send notifications.");
            }

            if (totalNotificationsSent != totalNotificationsToProcess) {
                throw new EmailQueueException("Wrong number of notifications processed.");
            }
        }
        catch (Exception ex) {
            logger.error("An error occurred trying to send notifications.", ex);
        }

        return totalNotificationsSent;
    }

    public void removeObsoleteReminders() {
        Map<String, List<EmailQueue>> records = EmailQueue.getRemindersForProcessing();
        if (records != null) {
            for (String key : records.keySet()) {
                if (AssignParticipantRoute.EMAIL_TYPE_REMINDER.equals(key)) {
                    List<EmailQueue> emailQueueList = records.get(key);
                    if (emailQueueList != null) {
                        for (EmailQueue email : emailQueueList) {
                            //get mr for given pt and check last_changed after email created date
                            if (email.getReminderCreated() != null) {
                                Long lastChanged = getLastChanged(email.getRecordId());
                                if (lastChanged != null && lastChanged > email.getReminderCreated() * 1000) {
                                    removeOldNotifications("", email.getRecordId());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static Long getLastChanged(@NonNull String participantId) {
        SimpleResult results = inTransaction((conn) -> {
            SimpleResult dbVals = new SimpleResult();
            try (PreparedStatement stmt = conn.prepareStatement(MedicalRecord.SQL_SELECT_MEDICAL_RECORD_LAST_CHANGED + " UNION " + OncHistoryDetail.SQL_SELECT_ONC_HISTORY_LAST_CHANGED
                    + " UNION " + Tissue.SQL_SELECT_TISSUE_LAST_CHANGED + " ORDER BY last_changed DESC LIMIT 1")) {
                stmt.setString(1, participantId);
                stmt.setString(2, participantId);
                stmt.setString(3, participantId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        dbVals.resultValue = rs.getLong(DBConstants.LAST_CHANGED);
                    }
                }
            }
            catch (SQLException ex) {
                dbVals.resultException = ex;
            }
            return dbVals;
        });

        if (results.resultException != null) {
            throw new RuntimeException("Error getting last_changed of medicalRecord of participant /w id " + participantId, results.resultException);
        }

        return (Long) results.resultValue;
    }

    public String getTemplate(@NonNull String templateName) {
        JsonElement notificationInfo = notificationLookup.get(templateName);
        return notificationInfo.getAsJsonObject().get(ApplicationConfigConstants.EMAIL_NOTIFICATIONS_SEND_GRID_TEMPLATE_ID).getAsString();
    }
}

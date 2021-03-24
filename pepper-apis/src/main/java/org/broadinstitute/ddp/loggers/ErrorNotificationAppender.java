package org.broadinstitute.ddp.loggers;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.spi.LoggingEvent;
import org.broadinstitute.ddp.email.SendGridClient;
import org.broadinstitute.ddp.util.Utility;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class ErrorNotificationAppender extends AppenderSkeleton {
    public static final String APPENDER_NAME = "errorNotification";

    private static SendGridClient emailClient;
    private static String emailAddress;
    private static String appEnv;
    private static String schedulerName;
    private static String emailSubject;
    private static boolean configured = false;
    private static AtomicLong minEpochForNextJobError = new AtomicLong(0);
    private static AtomicLong minEpochForNextError = new AtomicLong(0);
    private static final int JOB_DELAY = 60;
    private static final int NON_JOB_DELAY = 30;
    private static final String ERROR_MESSAGE = "<p>An error has been detected for <b>%s</b>. Please go check the backend logs around <b>%s UTC</b>.</p><p>%s</p><p>%s</p>";
    private static final String JOB_THROTTLE_NOTE = "This looks like a job error. Job error reporting is throttled so you will only see 1 per %s minutes.";
    private static final String NON_JOB_THROTTLE_NOTE = "This does NOT look like a job error. Non-job error reporting is throttled so you will only see 1 per %s minutes.";
    private static final String EXTRA_NOTE = "If something is going wrong and you need to turn off these notifications login to the <b>lddpuser</b> SendGrid account and " +
            "change the mail send permissions on the appropriate API KEY.";
    private static final String MSG_TYPE_JOB_ERROR = "JOB_ERROR_ALERT";
    private static final String MSG_TYPE_ERROR = "ERROR_ALERT";

    @Override
    protected void append(LoggingEvent event) {
        if (configured) {
            //we only want to report ERRORs via email
            if (event.getLevel().toInt() == Level.ERROR_INT) {
                try {
                    boolean jobError = (schedulerName != null)&&(event.getThreadName().contains(schedulerName));
                    long currentEpoch = Utility.getCurrentEpoch();

                    if ((jobError)&&(currentEpoch >= minEpochForNextJobError.get())) {
                        sendEmail(currentEpoch, String.format(JOB_THROTTLE_NOTE, JOB_DELAY), MSG_TYPE_JOB_ERROR);
                        minEpochForNextJobError.set(currentEpoch + (JOB_DELAY * 60));
                    }
                    else if ((!jobError)&&(currentEpoch >= minEpochForNextError.get())) {
                        sendEmail(currentEpoch, String.format(NON_JOB_THROTTLE_NOTE, NON_JOB_DELAY), MSG_TYPE_ERROR);
                        minEpochForNextError.set(currentEpoch + (NON_JOB_DELAY * 60));
                    }
                }
                catch (Exception ex) {
                    System.out.println("ErrorNotificationAppender Error: " + ExceptionUtils.getStackTrace(ex)); //DON'T LOG THIS!!!!!
                }
            }
        }
    }

    private void sendEmail(long currentEpoch, String note, String messageType) {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = formatter.format(new Date(currentEpoch * 1000));

        emailClient.sendSingleNonTemplate(emailAddress, emailSubject,
                String.format(ERROR_MESSAGE, appEnv, date, note, EXTRA_NOTE), messageType);
    }

    public void close() {
    }

    public boolean requiresLayout() {
        return false;
    }

    public synchronized static void configure(@NonNull Config config, String scheduler) {
        if (!configured) {
            appEnv = config.getString("portal.environment");
            emailAddress = config.getString("errorAlert.recipientAddress");
            emailClient = new SendGridClient();
            JsonObject settings =(JsonObject)(new JsonParser().parse(config.getString("errorAlert.clientSettings")));
            emailSubject = "Error Alert: " + settings.get("sendGridFromName").getAsString();
            emailClient.configure(config.getString("errorAlert.key"), settings,"", null, appEnv);
            schedulerName = scheduler;
            configured = true;
        }
        else {
            throw new RuntimeException("Configure has already been called for this appender.");
        }
    }
}
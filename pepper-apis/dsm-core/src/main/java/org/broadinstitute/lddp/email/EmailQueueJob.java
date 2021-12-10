package org.broadinstitute.lddp.email;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.typesafe.config.Config;
import lombok.NonNull;
import org.broadinstitute.lddp.datstat.DatStatUtil;
import org.broadinstitute.lddp.util.BasicTriggerListener;
import org.broadinstitute.lddp.util.EmailJobTriggerListener;
import org.quartz.*;
import org.quartz.impl.matchers.KeyMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import static org.quartz.SimpleScheduleBuilder.simpleSchedule;

/**
 * DatStat job that sends out participant notifications, validates addresses, creates kit request ids.
 */
public class EmailQueueJob implements org.quartz.Job {
    private static final Logger logger = LoggerFactory.getLogger(EmailQueueJob.class);

    private static final String LOG_PREFIX = "EMAILQUEUE JOB - ";
    private static final String SPLASH_PAGE = "SPLASH_PAGE";
    private static final String EMAIL_CLASS_NAME = "EMAIL_CLASS_NAME";
    private static final String EMAIL_KEY = "EMAIL_KEY";
    private static final String EMAIL_CLIENT_SETTINGS = "EMAIL_CLIENT_SETTINGS";
    private static final String FRONTEND_URL = "FRONTEND_URL";
    private static final String ENVIRONMENT = "ENVIRONMENT";

    public EmailQueueJob() {
        logger.info(LOG_PREFIX + "Instance created.");
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            boolean splashPageOnly = dataMap.getBoolean(SPLASH_PAGE);
            String emailClassName = dataMap.getString(EMAIL_CLASS_NAME);
            String emailKey = dataMap.getString(EMAIL_KEY);
            String emailClientSettings = dataMap.getString(EMAIL_CLIENT_SETTINGS);
            String portalFrontendUrl = dataMap.getString(FRONTEND_URL);
            String environment = dataMap.getString(ENVIRONMENT);

            //the method below will log an error not throw an exception
            sendQueuedNotifications(splashPageOnly, emailClassName, emailKey, emailClientSettings, portalFrontendUrl, environment);
        }
        catch (Exception ex) {
            logger.error(LOG_PREFIX + "Failed to execute properly.", ex);
        }
    }

    /**
     * 1) Determines which emails need to be sent by checking a DB queue
     * 2) Uses SendGrid to send emails
     * 3) Updates queue records after sending emails so they won't be sent again
     *
     * @return ids of emails sent
     */
    public static Collection<Long> sendQueuedNotifications(boolean splashPageOnly, String emailClassName, String emailKey,
                                                              String emailClientSettings, String portalFrontendUrl,
                                                              @NonNull String environment) {
        logger.debug(LOG_PREFIX + "Checking for queued notifications...");

        DatStatUtil datStatUtil = (splashPageOnly) ? null: new DatStatUtil();

        ArrayList<Long> ids = new ArrayList<>();

        try {
            EmailClient emailClient = (EmailClient) Class.forName(emailClassName).newInstance();
            emailClient.configure(emailKey, (JsonObject)(new JsonParser().parse(emailClientSettings)),
                    portalFrontendUrl, datStatUtil, environment);

            Map<String, ArrayList<EmailRecord>> records = EmailRecord.getRecordsForProcessing();

            if (EmailRecord.getRecordCount(records) > 0)
            {
                //loop through the different templates
                for (Map.Entry<String, ArrayList<EmailRecord>> templateRecords : records.entrySet())
                {
                    String template = templateRecords.getKey();

                    //loop through records for template
                    for (EmailRecord record : templateRecords.getValue())
                    {
                        try {
                            //note that there are 2 db updates here
                            EmailRecord.startProcessing(record.getRecordId());
                            emailClient.sendSingleEmail(template, record.getRecipient(), (!splashPageOnly) ? datStatUtil.getPortalNotificationAttachmentClassNames(template) : null);
                            EmailRecord.completeProcessing(record.getRecordId());
                            ids.add(record.getRecordId());
                        }
                        catch (Exception ex) {
                            logger.error(LOG_PREFIX + "Unable to send notification for email record = " + record.getRecordId() + ".", ex);
                        }
                    }
                }
            }
        }
        catch (Exception ex)
        {
            logger.error(LOG_PREFIX + "An error occurred trying to send notifications.", ex);
        }

        return ids;
    }

    /**
     * Adds job to scheduler that will send out queued notifications.
     */
    public static void createScheduledJob(Scheduler scheduler, Config config, boolean splashPageOnly)
    {
        int jobIntervalInSeconds = config.getInt("portal.emailJobIntervalInSeconds");

        try
        {
            //create job
            JobDetail job = JobBuilder.newJob(EmailQueueJob.class)
                    .withIdentity("EMAIL_QUEUE_JOB", BasicTriggerListener.NO_CONCURRENCY_GROUP + ".PORTAL")
                    .build();

            job.getJobDataMap().put(SPLASH_PAGE, splashPageOnly);
            job.getJobDataMap().put(EMAIL_CLASS_NAME, config.getString("email.className"));
            job.getJobDataMap().put(EMAIL_KEY, config.getString("email.key"));
            job.getJobDataMap().put(EMAIL_CLIENT_SETTINGS, config.getString("email.clientSettings"));
            job.getJobDataMap().put(FRONTEND_URL, config.getString("portal.frontendUrl"));
            job.getJobDataMap().put(ENVIRONMENT, config.getString("portal.environment"));

            //create trigger
            TriggerKey triggerKey = new TriggerKey("EMAIL_QUEUE_TRIGGER", "PORTAL");
            SimpleTrigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(
                            simpleSchedule()
                                    .withIntervalInSeconds(jobIntervalInSeconds)
                                    .repeatForever()
                    ).build();

            //add job
            scheduler.scheduleJob(job, trigger);

            //add trigger listener
            scheduler.getListenerManager().addTriggerListener(new EmailJobTriggerListener(), KeyMatcher.keyEquals(triggerKey));
        }
        catch (Exception ex)
        {
            throw new RuntimeException("Unable to schedule email queue job.", ex);
        }
    }
}

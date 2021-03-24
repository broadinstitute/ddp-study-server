package org.broadinstitute.dsm.jobs;

import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.util.EventUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DDPEventJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(DDPEventJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            //fetch parameters from JobDataMap
            EventUtil eventUtil = (EventUtil) dataMap.get(DSMServer.EVENT_UTIL);
            eventUtil.triggerReminder();

            NotificationUtil notificationUtil = (NotificationUtil) dataMap.get(DSMServer.NOTIFICATION_UTIL);
            notificationUtil.removeObsoleteReminders();
        }
        catch (Exception ex) {
            logger.error("Failed to execute properly.", ex);
        }
    }
}

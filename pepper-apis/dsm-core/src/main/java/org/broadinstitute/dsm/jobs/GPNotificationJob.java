package org.broadinstitute.dsm.jobs;

import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.broadinstitute.dsm.util.GPNotificationUtil;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GPNotificationJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(GPNotificationJob.class);

    /**
     * Job to schedule gp notifications
     * w/ information about number of spitKitRequests from all portals
     * adding notification into EMAIL_QUEUE
     * which then gets send by next run of NotificationJob
     * @param context JobExecutionContext
     * @throws JobExecutionException
     */
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            //fetch parameters from JobDataMap
            GPNotificationUtil scheduler = new GPNotificationUtil((Config) dataMap.get(DSMServer.CONFIG),
                    (NotificationUtil) dataMap.get(DSMServer.NOTIFICATION_UTIL),
                    (KitUtil) dataMap.get(DSMServer.KIT_UTIL));
            // Request KitRequests
            scheduler.queryAndWriteNotification();
        }
        catch (Exception ex) {
            logger.error("Failed to execute properly.", ex);
        }
    }
}

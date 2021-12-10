package org.broadinstitute.dsm.jobs;

import com.typesafe.config.Config;
import org.broadinstitute.dsm.DSMServer;
import org.broadinstitute.dsm.util.NotificationUtil;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(NotificationJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            Config config = (Config) dataMap.get(DSMServer.CONFIG);
            NotificationUtil notification = new NotificationUtil(config);
            notification.sendQueuedNotifications();
        }
        catch (Exception ex) {
            logger.error("Failed to execute properly.", ex);
        }
    }
}

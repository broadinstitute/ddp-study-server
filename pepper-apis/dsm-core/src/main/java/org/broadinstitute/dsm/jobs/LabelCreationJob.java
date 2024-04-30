package org.broadinstitute.dsm.jobs;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LabelCreationJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(LabelCreationJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            Long labelCreationStarted = DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING);
            Instant labelCreationStartedInstant = Instant.ofEpochMilli(labelCreationStarted);
            if (labelCreationStarted == 0) {
                List<KitRequestCreateLabel> kitsLabelTriggered = KitUtil.getListOfKitsLabelTriggered();
                if (!kitsLabelTriggered.isEmpty()) {
                    KitUtil.createLabel(kitsLabelTriggered, null);
                }
            } else if (labelCreationStartedInstant.isBefore(Instant.now().minus(1, ChronoUnit.HOURS))) {
                logger.error("Label creation job is running for over 1 hour now, will reset the job. Please check the logs for errors");
                // DSM is supposed to wait for 5 seconds for a carrier to respond, so in one hour DSM should be approx. able to
                // create 720 labels. If we add the time it takes to process a kit after the carrier responds, we should be able to
                // process about 500 kits in one hour. If we are still running after one hour, something was wrong, we will reset the job
                // and so the next time the job runs, it will pick up the kits that were not processed.
                DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING);

            } else {
                logger.info("Label creation job is still running, will not start a new job");
            }
        } catch (Exception ex) {
            logger.error("Error running LabelCreationJob", ex);
            DBUtil.updateBookmark(0, KitUtil.BOOKMARK_LABEL_CREATION_RUNNING);
        }
    }
}

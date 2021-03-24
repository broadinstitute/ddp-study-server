package org.broadinstitute.dsm.jobs;

import org.broadinstitute.dsm.db.KitRequestCreateLabel;
import org.broadinstitute.dsm.util.DBUtil;
import org.broadinstitute.dsm.util.KitUtil;
import org.broadinstitute.dsm.util.SystemUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class LabelCreationJob implements Job {

    private static final Logger logger = LoggerFactory.getLogger(LabelCreationJob.class);

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            Long labelCreationStarted = DBUtil.getBookmark(KitUtil.BOOKMARK_LABEL_CREATION_RUNNING);
            if (labelCreationStarted == 0) {
                List<KitRequestCreateLabel> kitsLabelTriggered = KitUtil.getListOfKitsLabelTriggered();
                if (!kitsLabelTriggered.isEmpty()) {
                    KitUtil.createLabel(kitsLabelTriggered);
                }
            }
            else if (labelCreationStarted < System.currentTimeMillis() - (SystemUtil.MILLIS_PER_HOUR * 2)) {
                logger.error("Label creation job is running for over 2 hours now...");
            }
        }
        catch (Exception ex) {
            logger.error("Failed to execute properly.", ex);
        }
    }
}

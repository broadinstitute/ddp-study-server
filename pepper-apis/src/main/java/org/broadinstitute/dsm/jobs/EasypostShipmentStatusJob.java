package org.broadinstitute.dsm.jobs;

import org.broadinstitute.dsm.util.KitUtil;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class EasypostShipmentStatusJob implements Job {

    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            KitUtil.getKitStatus();
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}

package org.broadinstitute.lddp.util;

import java.util.List;

import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.quartz.TriggerListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by ebaker on 5/25/16.
 */
public abstract class BasicTriggerListener implements TriggerListener {
    public static final String NO_CONCURRENCY_GROUP = "NO_CONCURRENCY";
    private static final Logger logger = LoggerFactory.getLogger(BasicTriggerListener.class);

    @Override
    public void triggerFired(Trigger trigger, JobExecutionContext context) {
        logger.debug(getName() + " - Trigger " + trigger.getKey() + " for job " + context.getJobDetail().getKey() + " --> Fired.");
    }

    @Override
    /**
     * Prevent new jobs that are in the "no concurrency" group from running concurrently.
     */
    public boolean vetoJobExecution(Trigger trigger, JobExecutionContext context) {
        boolean veto = false;

        try {
            if (context.getJobDetail().getKey().getGroup().contains(NO_CONCURRENCY_GROUP)) {
                List<JobExecutionContext> otherContexts = context.getScheduler().getCurrentlyExecutingJobs();
                for (JobExecutionContext otherContext : otherContexts) {

                    if (context.getJobDetail().equals(otherContext.getJobDetail())) {
                        logger.debug(getName() + " - Trigger " + trigger.getKey() + " for job " + context.getJobDetail().getKey() + " -->"
                                + " Concurrency check failed. Skipping job.");
                        veto = true;
                        break;
                    }
                }
            } else {
                logger.debug(getName() + " - Trigger " + trigger.getKey() + " for job " + context.getJobDetail().getKey() + " --> "
                        + "Concurrency check skipped.");
            }
        } catch (Exception ex) {
            veto = true;
            logger.error(getName() + " - Trigger " + trigger.getKey() + " for job " + context.getJobDetail().getKey() + " --> Error in "
                    + "vetoJobExecution", ex);
        }

        monitorJobExecution(veto);

        return veto;
    }

    @Override
    public void triggerMisfired(Trigger trigger) {
        logger.warn(getName() + " - Trigger " + trigger.getKey() + " --> Misfired");
    }

    @Override
    public void triggerComplete(Trigger trigger, JobExecutionContext context,
                                Trigger.CompletedExecutionInstruction triggerInstructionCode) {
        logger.info(getName() + " - Trigger " + trigger.getKey() + " for job " + context.getJobDetail().getKey() + " --> Completed");
    }

    protected abstract void monitorJobExecution(boolean veto);
}

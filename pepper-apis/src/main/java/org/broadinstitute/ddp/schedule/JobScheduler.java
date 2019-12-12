package org.broadinstitute.ddp.schedule;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.exception.DDPException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JobScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(JobScheduler.class);

    public static Scheduler initializeWith(Config cfg, Schedulable... schedulables) {
        Scheduler scheduler = createJobSchedulerOrThrow();
        try {
            for (Schedulable schedulable : schedulables) {
                schedulable.register(scheduler, cfg);
            }
            startSchedulerOrThrow(scheduler);
        } catch (Exception e) {
            // Shutdown and rethrow, otherwise application will stall waiting on the scheduler's internal thread pool.
            shutdownScheduler(scheduler, false);
            throw new DDPException(e);
        }
        return scheduler;
    }

    private static Scheduler createJobSchedulerOrThrow() {
        try {
            return new StdSchedulerFactory().getScheduler();
        } catch (SchedulerException e) {
            throw new DDPException("Failed to initialize job scheduler", e);
        }
    }

    private static void startSchedulerOrThrow(Scheduler scheduler) {
        try {
            scheduler.start();
            LOG.info("Started DDP job scheduler");
        } catch (SchedulerException e) {
            throw new DDPException("Failed to start job scheduler", e);
        }
    }

    public static void shutdownScheduler(Scheduler scheduler, boolean waitForJobs) {
        try {
            scheduler.shutdown(waitForJobs);
        } catch (SchedulerException e) {
            LOG.error("Error while shutting down job scheduler", e);
        }
    }

    /**
     * Represents something that can be scheduled or help schedule a job with a scheduler. Often used to abstract a method that helps
     * register a job/trigger.
     */
    @FunctionalInterface
    public interface Schedulable {
        /**
         * Registers job and/or trigger with the provided scheduler.
         *
         * @param scheduler the scheduler
         * @param cfg       the application configurations
         * @throws SchedulerException if error while registering with scheduler
         */
        void register(Scheduler scheduler, Config cfg) throws SchedulerException;
    }
}

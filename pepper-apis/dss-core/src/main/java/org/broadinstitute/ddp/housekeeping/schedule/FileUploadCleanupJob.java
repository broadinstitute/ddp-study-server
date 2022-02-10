package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.quartz.CronScheduleBuilder;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class FileUploadCleanupJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadCleanupJob.class);

    private static FileUploadService service;

    public static JobKey getKey() {
        return Keys.Cleanup.FileUploadJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        service = FileUploadService.fromConfig(cfg);
        JobDetail job = JobBuilder.newJob(FileUploadCleanupJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.FILE_UPLOAD_CLEANUP_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Cleanup.FileUploadTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(schedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger {} for job {} with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();

            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS,
                    handle -> service.removeUnusedUploads(handle));

            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Job {} completed in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.DatabaseBackup;
import org.quartz.CronScheduleBuilder;
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

public class DatabaseBackupCheckJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackupCheckJob.class);

    public static JobKey getKey() {
        return Keys.DbBackups.CheckJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String backupCheckSchedule = cfg.getString(ConfigFile.DB_BACKUP_CHECK_SCHEDULE);
        if (backupCheckSchedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        JobDetail job = JobBuilder.newJob(DatabaseBackupCheckJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job '{}' to scheduler", getKey());

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.GcpOps.BackupCheckTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(backupCheckSchedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger '{}' for job '{}' with schedule '{}'", trigger.getKey(), getKey(), backupCheckSchedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.info("Running DatabaseBackup check job '{}' ", getKey());
            long start = Instant.now().toEpochMilli();
            DatabaseBackup dbBackup = new DatabaseBackup();
            dbBackup.checkBackupJobs();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Database backup check job '{}' completed in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing Database backup job '{}' ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

}

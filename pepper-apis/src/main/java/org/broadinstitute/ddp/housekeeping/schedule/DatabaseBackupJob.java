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

public class DatabaseBackupJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackupJob.class);

    public static JobKey getKey() {
        return Keys.DbBackups.RequestJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String backupSchedule = cfg.getString(ConfigFile.DB_BACKUP_SCHEDULE);
        if (backupSchedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        JobDetail job = JobBuilder.newJob(DatabaseBackupJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job '{}' to scheduler", getKey());

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.GcpOps.DbBackupTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(backupSchedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger '{}' for job '{}' with schedule '{}'", trigger.getKey(), getKey(), backupSchedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.info("Running DatabaseBackup job '{}' ", getKey());
            long start = Instant.now().toEpochMilli();
            DatabaseBackup dbBackup = new DatabaseBackup();
            dbBackup.createBackups();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Database backup job '{}' completed in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing Database backup job '{}' ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

}

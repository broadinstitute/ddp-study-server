package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.housekeeping.dao.KitCheckDao;
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
public class CheckKitsJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(CheckKitsJob.class);

    public static JobKey getKey() {
        return Keys.Kits.CheckJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(CheckKitsJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.CHECK_KITS_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Kits.CheckTrigger)
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
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();
            TransactionWrapper.useTxn(
                    TransactionWrapper.DB.APIS,
                    handle -> new KitCheckDao().scheduleKits(handle));
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Job {} completed in {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while executing job {} ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

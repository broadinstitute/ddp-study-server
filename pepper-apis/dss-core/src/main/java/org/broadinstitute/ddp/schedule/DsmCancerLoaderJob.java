package org.broadinstitute.ddp.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.housekeeping.schedule.Keys;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.ConfigUtil;
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

@Slf4j
public class DsmCancerLoaderJob implements Job {
    public static JobKey getKey() {
        return Keys.Loader.CancerJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        JobDetail cancerLoaderJob = JobBuilder.newJob(DsmCancerLoaderJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();

        scheduler.addJob(cancerLoaderJob, true);
        log.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.CANCER_LOADER_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            log.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Loader.CancerTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(schedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        log.info("Added trigger {} for job {} with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            log.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();

            var dsm = new DsmClient(ConfigManager.getInstance().getConfig());
            var result = dsm.listCancers();
            if (result.getStatusCode() == 200) {
                List<String> names = result.getBody();
                CancerStore.getInstance().populate(names);
                log.info("Loaded {} cancers into pepper", names == null ? 0 : names.size());
            } else {
                log.error("Could not fetch DSM cancer list, got response status code {}",
                        result.getStatusCode(), result.getThrown());
            }

            long elapsed = Instant.now().toEpochMilli() - start;
            log.info("Completed job {} in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            log.error("Error while executing job {}", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

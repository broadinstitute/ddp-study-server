package org.broadinstitute.ddp.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.housekeeping.schedule.Keys;
import org.broadinstitute.ddp.model.dsm.DrugStore;
import org.broadinstitute.ddp.util.ConfigManager;
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

public class DsmDrugLoaderJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(DsmDrugLoaderJob.class);

    public static JobKey getKey() {
        return Keys.Loader.DrugJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String schedule = cfg.getString(ConfigFile.DRUG_LOADER_SCHEDULE);
        if (schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        JobDetail drugLoaderJob = JobBuilder.newJob(DsmDrugLoaderJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(drugLoaderJob, true);
        LOG.info("Added job '{}' to scheduler", getKey());

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Loader.DrugTrigger)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(schedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger '{}' for job '{}' with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            LOG.info("Running job '{}'", getKey());
            long start = Instant.now().toEpochMilli();

            var dsm = new DsmClient(ConfigManager.getInstance().getConfig());
            var result = dsm.listDrugs();
            if (result.getStatusCode() == 200) {
                DrugStore.getInstance().populateDrugList(result.getBody());
            } else {
                LOG.error("Could not fetch DSM drug list, got response status code {}",
                        result.getStatusCode(), result.getThrown());
            }

            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Completed job '{}' in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing job '{}'", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

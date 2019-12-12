package org.broadinstitute.ddp.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.util.DsmDrugLoader;
import org.quartz.CronScheduleBuilder;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
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

    public static final String NAME = "drug";
    public static final String GROUP = "loader";
    private static final String TRIGGER_GROUP = "dsm";
    private static final String TRIGGER_NAME = "drug-loader";

    private static final Logger LOG = LoggerFactory.getLogger(DsmDrugLoaderJob.class);

    public static JobKey getKey() {
        return JobKey.jobKey(NAME, GROUP);
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String schedule = cfg.getString(ConfigFile.DRUG_LOADER_SCHEDULE);
        if (schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        JobDataMap dataMap = new JobDataMap();
        dataMap.put(ConfigFile.DSM_JWT_SECRET, cfg.getString(ConfigFile.DSM_JWT_SECRET));
        dataMap.put(ConfigFile.DSM_BASE_URL, cfg.getString(ConfigFile.DSM_BASE_URL));

        JobDetail drugLoaderJob = JobBuilder.newJob(DsmDrugLoaderJob.class)
                .withIdentity(getKey())
                .usingJobData(dataMap)
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(drugLoaderJob, true);
        LOG.info("Added drug loader job '{}' to scheduler", getKey());

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(TRIGGER_NAME, TRIGGER_GROUP)
                .forJob(getKey())
                .withSchedule(CronScheduleBuilder
                        .cronSchedule(schedule)
                        .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                        .withMisfireHandlingInstructionFireAndProceed())
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger '{}' for drug loader job '{}' with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getMergedJobDataMap();
            LOG.info("Running DsmDrugDataLoader job '{}'", getKey());
            long start = Instant.now().toEpochMilli();
            DsmDrugLoader drugLoader = new DsmDrugLoader();
            drugLoader.fetchAndLoadDrugs(dataMap.getString(ConfigFile.DSM_BASE_URL),
                    dataMap.getString(ConfigFile.DSM_JWT_SECRET));
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Dsm Drug Loader job '{}' completed in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing DsmDrugLoader job '{}'", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

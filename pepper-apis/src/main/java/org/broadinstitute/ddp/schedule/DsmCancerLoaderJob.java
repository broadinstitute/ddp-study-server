package org.broadinstitute.ddp.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.CancerStore;
import org.broadinstitute.ddp.service.DsmCancerListService;
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

public class DsmCancerLoaderJob implements Job {

    public static final String NAME = "cancer";
    public static final String GROUP = "loader";
    public static final String TRIGGER_GROUP = "dsm";
    private static final String TRIGGER_NAME = "cancer-loader";

    private static final Logger LOG = LoggerFactory.getLogger(DsmCancerLoaderJob.class);

    public static JobKey getKey() {
        return JobKey.jobKey(NAME, GROUP);
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String schedule = cfg.getString(ConfigFile.CANCER_LOADER_SCHEDULE);
        if (schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        JobDataMap dataMap = new JobDataMap();
        dataMap.put(ConfigFile.DSM_JWT_SECRET, cfg.getString(ConfigFile.DSM_JWT_SECRET));
        dataMap.put(ConfigFile.DSM_BASE_URL, cfg.getString(ConfigFile.DSM_BASE_URL));

        JobDetail cancerLoaderJob = JobBuilder.newJob(DsmCancerLoaderJob.class)
                .withIdentity(getKey())
                .usingJobData(dataMap)
                .requestRecovery(false)
                .storeDurably(true)
                .build();

        scheduler.addJob(cancerLoaderJob, true);
        LOG.info("Added cancer loader job '{}' to scheduler", getKey());

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
        LOG.info("Added trigger '{}' for cancer loader job '{}' with schedule '{}'", trigger.getKey(), getKey(), schedule);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        try {
            JobDataMap dataMap = context.getMergedJobDataMap();
            LOG.info("Running DsmCancerDataLoader job '{}'", getKey());
            long start = Instant.now().toEpochMilli();
            DsmCancerListService service = new DsmCancerListService(dataMap.getString(ConfigFile.DSM_BASE_URL));
            List<String> cancerNames = service.fetchCancerList(dataMap.getString(ConfigFile.DSM_JWT_SECRET));
            CancerStore.getInstance().populate(cancerNames);
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Dsm Cancer Loader job '{}' completed in {}ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing DsmCancerLoader job '{}'", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }
}

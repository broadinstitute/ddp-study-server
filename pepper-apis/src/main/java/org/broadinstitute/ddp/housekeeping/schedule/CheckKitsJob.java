package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.housekeeping.dao.KitCheckDao;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@DisallowConcurrentExecution
public class CheckKitsJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(CheckKitsJob.class);
    private static final int DELAY_SECS = 30;

    /**
     * Key is study guid, value is the metrics transmitter for the study
     */
    private static final Map<String, StackdriverMetricsTracker> kitCounterMonitorByStudy = new HashMap<>();

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

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Kits.CheckTrigger)
                .forJob(getKey())
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(DELAY_SECS))
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger {} for job {} with delay of {} seconds", trigger.getKey(), getKey(), DELAY_SECS);
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                var kitCheckDao = new KitCheckDao();
                var result = kitCheckDao.checkForInitialKits(handle);
                result.add(kitCheckDao.scheduleNextKits(handle));
                sendKitMetrics(result);
            });
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Job {} completed in {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while executing job {} ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void sendKitMetrics(KitCheckDao.KitCheckResult kitCheckResult) {
        for (var queuedParticipantsByStudy : kitCheckResult.getQueuedParticipantsByStudy()) {
            String studyGuid = queuedParticipantsByStudy.getKey();
            int numQueuedParticipants = queuedParticipantsByStudy.getValue().size();
            var tracker = kitCounterMonitorByStudy.computeIfAbsent(studyGuid, key ->
                    new StackdriverMetricsTracker(StackdriverCustomMetric.KITS_REQUESTED, studyGuid,
                            PointsReducerFactory.buildSumReducer()));
            tracker.addPoint(numQueuedParticipants, Instant.now().toEpochMilli());
        }
    }
}

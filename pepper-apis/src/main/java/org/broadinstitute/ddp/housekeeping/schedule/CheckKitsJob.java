package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.client.DsmClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.broadinstitute.ddp.service.KitCheckService;
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

    /**
     * Key is study guid, value is the metrics transmitter for the study
     */
    private static final Map<String, StackdriverMetricsTracker> kitCounterMonitorByStudy = new HashMap<>();
    private static KitCheckService kitCheckService = null;
    private static DsmClient dsmClient = null;
    private static long lastStatusCheckEpochSecs = -1;
    private static long statusCheckSecs = -1;

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

        if (!cfg.getBoolean(ConfigFile.Kits.CHECK_ENABLED)) {
            LOG.warn("Job {} is disabled, no trigger added", getKey());
            return;
        }

        int intervalSecs = cfg.getInt(ConfigFile.Kits.INTERVAL_SECS);
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.Kits.CheckTrigger)
                .forJob(getKey())
                .withSchedule(SimpleScheduleBuilder.repeatSecondlyForever(intervalSecs))
                .startNow()
                .build();
        scheduler.scheduleJob(trigger);
        LOG.info("Added trigger {} for job {} with delay of {} seconds", trigger.getKey(), getKey(), intervalSecs);

        kitCheckService = new KitCheckService();
        dsmClient = new DsmClient(cfg);
        statusCheckSecs = cfg.getLong(ConfigFile.Kits.STATUS_CHECK_SECS);
        LOG.info("Job {} status check seconds is set to {}", getKey(), statusCheckSecs);
    }

    @Override
    public void execute(JobExecutionContext ctx) throws JobExecutionException {
        try {
            LOG.info("Running job {}", getKey());
            long start = Instant.now().toEpochMilli();

            // We don't want to bombard DSM with kit status calls, so only call if time is up.
            if (Instant.now().getEpochSecond() - lastStatusCheckEpochSecs > statusCheckSecs) {
                TransactionWrapper.useTxn(TransactionWrapper.DB.APIS,
                        handle -> kitCheckService.checkPendingKitSentStatuses(handle, dsmClient));
                lastStatusCheckEpochSecs = Instant.now().getEpochSecond();
            }

            KitCheckService.KitCheckResult result = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle -> {
                LOG.info("Checking for initial kits");
                return kitCheckService.checkForInitialKits(handle);
            });

            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                LOG.info("Checking for recurring kits");
                result.add(kitCheckService.scheduleNextKits(handle));
                sendKitMetrics(result);
            });

            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Job {} completed in {}s", getKey(), elapsed / 1000);
        } catch (Exception e) {
            LOG.error("Error while executing job {} ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void sendKitMetrics(KitCheckService.KitCheckResult kitCheckResult) {
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

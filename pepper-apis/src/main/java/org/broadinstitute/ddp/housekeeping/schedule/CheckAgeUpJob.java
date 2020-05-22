package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.StudyGovernanceDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.service.AgeUpService;
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
public class CheckAgeUpJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(CheckAgeUpJob.class);

    public static JobKey getKey() {
        return Keys.AgeUp.CheckJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        JobDetail job = JobBuilder.newJob(CheckAgeUpJob.class)
                .withIdentity(getKey())
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job {} to scheduler", getKey());

        String schedule = ConfigUtil.getStrIfPresent(cfg, ConfigFile.CHECK_AGE_UP_SCHEDULE);
        if (schedule == null || schedule.equalsIgnoreCase("off")) {
            LOG.warn("Job {} is set to be turned off, no trigger added", getKey());
            return;
        }

        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(Keys.AgeUp.CheckTrigger)
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
            run();
            long elapsed = Instant.now().toEpochMilli() - start;
            LOG.info("Job {} completed in {} ms", getKey(), elapsed);
        } catch (Exception e) {
            LOG.error("Error while executing job {} ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private void run() {
        AgeUpService service = new AgeUpService();
        PexInterpreter interpreter = new TreeWalkInterpreter();
        List<GovernancePolicy> policies = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS, handle ->
                handle.attach(StudyGovernanceDao.class)
                        .findAllPolicies()
                        .collect(Collectors.toList()));
        Collections.shuffle(policies);
        for (var policy : policies) {
            if (policy.getAgeOfMajorityRules().isEmpty()) {
                LOG.info("Study {} has no age-of-majority rules, skipping", policy.getStudyGuid());
                continue;
            }
            try {
                LOG.info("Running age-up check for study {}", policy.getStudyGuid());
                // Perform check for each study within a transaction so any unhandled errors for study will not affect other studies.
                TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> service.runAgeUpCheck(handle, interpreter, policy));
                LOG.info("Finished age-up check for study {}", policy.getStudyGuid());
            } catch (DDPException e) {
                LOG.error("Error while checking age-up for study {}, continuing", policy.getStudyGuid(), e);
            }
        }
    }
}

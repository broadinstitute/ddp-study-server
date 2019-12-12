package org.broadinstitute.ddp.housekeeping.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.ActivityDefStore;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.export.DataExporter;
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

public class StudyExportToESJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(StudyExportToESJob.class);
    private static final String DATA_EXPORTER = "exporter";
    private static final String DATA_STUDY_GUID = "studyGuid";

    public static JobKey getKey() {
        return Keys.EsExport.StudyJob;
    }

    public static void register(Scheduler scheduler, Config cfg) throws SchedulerException {
        String exportSchedule = cfg.getString(ConfigFile.STUDY_EXPORT_TO_ES_SCHEDULE);
        if (exportSchedule.equalsIgnoreCase("off")) {
            LOG.warn("Job '{}' is set to be turned off", getKey());
            return;
        }

        DataExporter exporter = new DataExporter(cfg);

        JobDataMap map = new JobDataMap();
        map.put(DATA_EXPORTER, exporter);

        JobDetail job = JobBuilder.newJob(StudyExportToESJob.class)
                .withIdentity(getKey())
                .usingJobData(map)
                .requestRecovery(false)
                .storeDurably(true)
                .build();
        scheduler.addJob(job, true);
        LOG.info("Added job '{}' to scheduler", getKey());

        List<StudyDto> studies = TransactionWrapper.withTxn(TransactionWrapper.DB.APIS,
                handle -> handle.attach(JdbiUmbrellaStudy.class).findAll());
        LOG.info("Will schedule elasticsearch export jobs for {} studies", studies.size());

        for (StudyDto studyDto : studies) {
            JobDataMap triggerData = new JobDataMap();
            triggerData.put(DATA_STUDY_GUID, studyDto.getGuid());

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity(studyDto.getGuid(), Keys.EsExport.StudyTriggerGroup)
                    .forJob(getKey())
                    .usingJobData(triggerData)
                    .withSchedule(CronScheduleBuilder
                            .cronSchedule(exportSchedule)
                            .inTimeZone(TimeZone.getTimeZone(ZoneId.of("UTC")))
                            .withMisfireHandlingInstructionFireAndProceed())    // If missed or late, just fire it.
                    .startNow()
                    .build();

            scheduler.scheduleJob(trigger);
            LOG.info("Added trigger '{}' for job '{}' with schedule '{}'", trigger.getKey(), getKey(), exportSchedule);
        }
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap jobDataMap = context.getMergedJobDataMap();
        DataExporter exporter = (DataExporter) jobDataMap.get(DATA_EXPORTER);
        String studyGuid = jobDataMap.getString(DATA_STUDY_GUID);

        try {
            TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
                StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                if (!studyDto.isDataExportEnabled()) {
                    LOG.warn("Study '{}' does not have data export enabled, skipping job '{}'", studyGuid, getKey());
                    return;
                }

                LOG.info("Running StudyExportToES job '{}' ", getKey());

                // Invalidate the caches for a fresh export
                ActivityDefStore.getInstance().clear();
                DataExporter.clearCachedAuth0Emails();

                // Export a "flat" document to ES
                LOG.info("Inside the StudyExportToES job '{}'. Doing the regular export ", getKey());
                long elapsed = estimateExecutionTime(
                        () -> exporter.exportParticipantsToElasticsearch(handle, studyDto, false)
                );
                LOG.info("Elasticsearch export (regular)'{}' completed in {}ms", getKey(), elapsed);

                // Export a "study-centric" document to ES
                LOG.info("Inside the StudyExportToES job '{}'. Doing the study-centric export ", getKey());
                elapsed = estimateExecutionTime(
                        () -> exporter.exportParticipantsToElasticsearch(handle, studyDto, true)
                );
                LOG.info("Elasticsearch export (study-centric)'{}' completed in {}ms", getKey(), elapsed);
            });
        } catch (Exception e) {
            LOG.error("Error while executing elasticsearch export job '{}' ", getKey(), e);
            throw new JobExecutionException(e, false);
        }
    }

    private long estimateExecutionTime(Runnable func) {
        long start = Instant.now().toEpochMilli();
        func.run();
        return Instant.now().toEpochMilli() - start;
    }
}

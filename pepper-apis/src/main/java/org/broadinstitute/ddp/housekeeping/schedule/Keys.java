package org.broadinstitute.ddp.housekeeping.schedule;

import org.quartz.JobKey;
import org.quartz.TriggerKey;

/**
 * Consolidation of all the job and trigger keys, to get an overview of what belongs to which group, so we avoid conflicts.
 */
public class Keys {
    public static class AgeUp {
        public static final JobKey CheckJob = JobKey.jobKey("check", "age-up");
        public static final TriggerKey CheckTrigger = TriggerKey.triggerKey("check", "age-up");
    }

    public static class Cleanup {
        public static final JobKey TempUserJob = JobKey.jobKey("temp-user", "cleanup");
        public static final TriggerKey TempUserTrigger = TriggerKey.triggerKey("temp-user", "cleanup");
    }

    public static class DbBackups {
        public static final JobKey RequestJob = JobKey.jobKey("request", "db-backups");
        public static final JobKey CheckJob = JobKey.jobKey("check", "db-backups");
    }

    public static class EsExport {
        public static final String StudyTriggerGroup = "study-export-es";
        public static final JobKey StudyJob = JobKey.jobKey("study", "es-export");
        public static final JobKey SyncJob = JobKey.jobKey("sync", "es-export");
        public static final TriggerKey SyncTrigger = TriggerKey.triggerKey("sync", "es-export");
    }

    public static class Export {
        public static final String StudyTriggerGroup = "study-export";
        public static final JobKey StudyJob = JobKey.jobKey("study", "export");
    }

    public static class GcpOps {
        public static final TriggerKey DbBackupTrigger = TriggerKey.triggerKey("db-backup", "gcp-operations");
        public static final TriggerKey BackupCheckTrigger = TriggerKey.triggerKey("backup-check", "gcp-operations");
    }

    public static class Loader {
        public static final JobKey CancerJob = JobKey.jobKey("cancer", "loader");
        public static final JobKey DrugJob = JobKey.jobKey("drug", "loader");
        public static final TriggerKey CancerTrigger = TriggerKey.triggerKey("cancer-loader", "dsm");
        public static final TriggerKey DrugTrigger = TriggerKey.triggerKey("drug-loader", "dsm");
    }
}

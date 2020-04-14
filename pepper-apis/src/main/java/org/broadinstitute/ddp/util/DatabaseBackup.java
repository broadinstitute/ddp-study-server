package org.broadinstitute.ddp.util;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.BackupRun;
import com.google.api.services.sqladmin.model.Operation;
import com.google.api.services.sqladmin.model.OperationError;
import com.google.api.services.sqladmin.model.OperationErrors;
import com.typesafe.config.Config;
import org.apache.commons.collections4.CollectionUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.housekeeping.BackupJobDto;
import org.broadinstitute.ddp.db.housekeeping.dao.JdbiBackupJob;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.monitoring.PointsReducerFactory;
import org.broadinstitute.ddp.monitoring.StackdriverCustomMetric;
import org.broadinstitute.ddp.monitoring.StackdriverMetricsTracker;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseBackup {

    private static final String CLOUDSQL_CRED_SCOPE = "https://www.googleapis.com/auth/sqlservice.admin";
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBackup.class);
    private static final String LABEL_KEY = "db";
    private static final String RUN_STATUS_DONE = "DONE";
    private static final String RUN_STATUS_HOPELESS = "HOPELESS";
    private static final String APP_NAME = "DatabaseBackupApp";
    private SQLAdmin sqlAdminService;
    private Config cfg;

    public DatabaseBackup() {
        cfg = ConfigManager.getInstance().getConfig();
        try {
            sqlAdminService = createSqlAdminService();
        } catch (IOException | GeneralSecurityException e) {
            LOG.error("Failed to initialize SQL Admin ", e);
            throw new DDPException("Failed to initialize SQL Admin ", e);
        }
    }

    public List<Operation> createBackups() {
        String gcpProject = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        String instanceId = cfg.getString(ConfigFile.DB_INSTANCE_ID);
        return TransactionWrapper.withTxn(TransactionWrapper.DB.HOUSEKEEPING,
                handle -> List.of(createBackup(handle, gcpProject, instanceId)));
    }

    public void checkBackupJobs() {
        String gcpProject = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        TransactionWrapper.useTxn(TransactionWrapper.DB.HOUSEKEEPING, handle -> {
            List<BackupJobDto> pendingBackups = getPendingJobs(handle);

            JdbiBackupJob jdbiBackupJob = handle.attach(JdbiBackupJob.class);
            for (BackupJobDto dto : pendingBackups) {
                Operation response = getOperation(dto.getRunName(), gcpProject);
                if (response == null) {
                    LOG.error("Null response for operation: {} get ", dto.getRunName());
                } else if (response.getError() != null && CollectionUtils.isNotEmpty(response.getError().getErrors())) {
                    logErrors(dto.getDatabaseName(), response.getError());
                } else if (RUN_STATUS_DONE.equalsIgnoreCase(response.getStatus())) {
                    updateBackupJobTable(jdbiBackupJob, dto.getRunName(), response.getEndTime().getValue(), response.getStatus());
                    postStackDriverMetric(dto.getDatabaseName());
                } else {
                    //check if submittedTime > 6hrs
                    Instant start = Instant.ofEpochMilli(dto.getStartTime());
                    Instant startPlus6Hrs = start.plus(6, ChronoUnit.HOURS);
                    if (Instant.now().isAfter(startPlus6Hrs)) {
                        LOG.error("Backup job for database instance {} failed to complete.", dto.getDatabaseName());
                        updateBackupJobTable(jdbiBackupJob, dto.getRunName(), null, RUN_STATUS_HOPELESS);
                    }
                }
            }
        });
    }


    private void updateBackupJobTable(JdbiBackupJob jdbiBackupJob, String runName, Long endTime, String status) {
        int rowCount = jdbiBackupJob.updateEndTimeStatus(runName, endTime, status);
        if (rowCount != 1) {
            LOG.error("{} rows updated in backup_job for run name: {} ", rowCount, runName);
        }
    }


    private List<BackupJobDto> getPendingJobs(Handle handle) {
        JdbiBackupJob backupJob = handle.attach(JdbiBackupJob.class);
        return backupJob.getPendingBackupJobs();
    }


    private Operation createBackup(Handle handle, String project, String instance) {
        Operation response = submitBackupRequest(project, instance);
        processResponse(handle, instance, response);
        return response;
    }


    private Operation submitBackupRequest(String project, String instance) {
        Operation response = null;
        BackupRun requestBody = new BackupRun();
        try {
            SQLAdmin.BackupRuns.Insert request = sqlAdminService.backupRuns().insert(project, instance, requestBody);
            response = request.execute();
        } catch (IOException e) {
            LOG.error("Failed submitting backup request for instance {} ", instance, e);
            //continue
        }
        return response;
    }


    private void processResponse(Handle handle, String instance, Operation response) {

        if (response == null) {
            LOG.error("null response from database {} backup request ", instance);
            return;
        } else if (response.getError() != null && CollectionUtils.isNotEmpty(response.getError().getErrors())) {
            logErrors(instance, response.getError());
            return;
        }

        String status = response.getStatus();
        if (RUN_STATUS_DONE.equalsIgnoreCase(status)) {
            postStackDriverMetric(instance);
        } else {
            saveJobDetails(handle, instance, response);
        }
        LOG.info("Submitted backup request: {} for DB: {} ", response.getName(), instance);
    }

    private void saveJobDetails(Handle handle, String instance, Operation response) {
        Long endTime = null;
        if (response.getEndTime() != null) {
            endTime = response.getEndTime().getValue();
        }
        //add a row into backup_job table
        JdbiBackupJob backupJob = handle.attach(JdbiBackupJob.class);
        backupJob.insert(response.getName(), response.getInsertTime().getValue(), endTime, instance, response.getStatus());
    }


    private void logErrors(String instance, OperationErrors errors) {
        StringBuilder errorStrings = new StringBuilder();
        for (OperationError error : errors.getErrors()) {
            errorStrings.append("Code: " + error.getCode());
            errorStrings.append("Kind: " + error.getKind());
            errorStrings.append("Message: " + error.getMessage());
            errorStrings.append("\n");
        }
        LOG.error(" Errors from database " + instance + "request \n " + errorStrings.toString());
    }


    private void postStackDriverMetric(String instance) {
        Map<String, String> labels = new HashMap<String, String>();
        labels.put(LABEL_KEY, instance);

        new StackdriverMetricsTracker(
                StackdriverCustomMetric.DB_BACKUP, PointsReducerFactory.buildSumReducer(), labels
        ).addPoint(1, Instant.now().toEpochMilli());

    }


    private Operation getOperation(String operationName, String project) {
        Operation response = null;
        try {
            SQLAdmin.Operations.Get request = sqlAdminService.operations().get(project, operationName);
            response = request.execute();
        } catch (IOException e) {
            LOG.error("Failed to get backup operation details for run {} ", operationName, e);
            //continue to next pending job
        }

        return response;
    }


    private SQLAdmin createSqlAdminService() throws IOException, GeneralSecurityException {
        HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        GoogleCredential credential = GoogleCredential.getApplicationDefault();
        if (credential.createScopedRequired()) {
            credential = credential.createScoped(Arrays.asList(CLOUDSQL_CRED_SCOPE));
        }

        return new SQLAdmin.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName(APP_NAME)
                .build();
    }

}

package org.broadinstitute.ddp.db.housekeeping.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.housekeeping.BackupJobDto;
import org.broadinstitute.ddp.util.LiquibaseUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JdbiBackupJobTest extends TxnAwareBaseTest {

    private static final String RUN_NAME = "testrun-1234-abcd-1xyz";
    private static final String RUN_STATUS = "PENDING";
    private static final String RUN_DB = "pepper-dev";
    private static final Logger LOG = LoggerFactory.getLogger(JdbiBackupJobTest.class);

    @Test
    public void testJdbiBackupJobMethods() {
        //Combined All method tests into one test to:
        //avoid inserting test data and deleting the data after the tests.
        //avoid coding a delete method for tests when its not needed for actual App code.
        initializeDb(cfg);
        long startTime = Instant.now().toEpochMilli();
        TransactionWrapper.useTxn(TransactionWrapper.DB.HOUSEKEEPING, handle -> {
            JdbiBackupJob backupJob = handle.attach(JdbiBackupJob.class);
            Long jobId = backupJob.insert(RUN_NAME, startTime, null, RUN_DB, RUN_STATUS);
            assertNotNull(jobId);

            //invoke other method tests
            testGet(handle);
            testGetPendingJobs(handle);
            testUpdateJob(handle);

            handle.rollback();
        });
    }

    private void testGet(Handle handle) {
        Optional<BackupJobDto> dto = handle.attach(JdbiBackupJob.class).getBackupJob(RUN_NAME);
        assertNotNull(dto);
        assertNotNull(dto.get());
        assertEquals(RUN_NAME, dto.get().getRunName());
        assertEquals(RUN_STATUS, dto.get().getStatus());
        assertEquals(RUN_DB, dto.get().getDatabaseName());
    }

    private void testGetPendingJobs(Handle handle) {
        List<BackupJobDto> jobList = handle.attach(JdbiBackupJob.class).getPendingBackupJobs();
        assertNotNull(jobList);
        assertTrue(jobList.size() > 0);
        Optional<BackupJobDto> backupJob = jobList.stream()
                .filter(job -> RUN_NAME.equals(job.getRunName())).findAny();
        assertNotNull(backupJob);
        assertTrue(backupJob.isPresent());
        assertEquals(RUN_NAME, backupJob.get().getRunName());
    }

    private void testUpdateJob(Handle handle) {
        Long endTime = Instant.now().toEpochMilli();
        String doneStatus = "DONE";
        int rowCount = handle.attach(JdbiBackupJob.class)
                .updateEndTimeStatus(RUN_NAME, endTime, doneStatus);

        assertTrue(rowCount == 1);

        //query run and compare endTime and status
        Optional<BackupJobDto> dto = handle.attach(JdbiBackupJob.class).getBackupJob(RUN_NAME);
        assertNotNull(dto);
        assertTrue(dto.isPresent());
        assertEquals(RUN_NAME, dto.get().getRunName());
        assertEquals(doneStatus, dto.get().getStatus());
        assertEquals(endTime, dto.get().getEndTime());
    }

    private static void initializeDb(Config cfg) {
        int maxConnections = cfg.getInt(ConfigFile.NUM_POOLED_CONNECTIONS);
        String dbUrl = cfg.getString(TransactionWrapper.DB.APIS.getDbUrlConfigKey());
        LiquibaseUtil.runLiquibase(dbUrl, TransactionWrapper.DB.HOUSEKEEPING);

        TransactionWrapper.init(
                new TransactionWrapper.DbConfiguration(TransactionWrapper.DB.HOUSEKEEPING, maxConnections, dbUrl));
    }

}

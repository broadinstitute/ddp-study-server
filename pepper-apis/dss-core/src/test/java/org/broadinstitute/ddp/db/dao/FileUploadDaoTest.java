package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class FileUploadDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static long studyId;
    private static long userId;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        studyId = testData.getStudyId();
        userId = testData.getUserId();
    }

    @Test
    public void testCreateAuthorized() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            FileUpload upload = dao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 123L);
            assertTrue(upload.getId() > 0);
            assertTrue(StringUtils.isNotBlank(upload.getGuid()));

            FileUpload actual = dao.findById(upload.getId()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getId(), actual.getId());

            actual = dao.findByGuid(upload.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getGuid(), actual.getGuid());
            assertNotNull(actual.getCreatedAt());
            assertNull("should not be marked uploaded yet", actual.getUploadedAt());
            assertNull("should not be scanned yet", actual.getScannedAt());
            assertNull(actual.getScanResult());

            handle.rollback();
        });
    }

    @Test
    public void testMarkVerified() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            FileUpload upload = dao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 123L);
            assertFalse(upload.isVerified());

            dao.markVerified(upload.getId());
            FileUpload actual = dao.findById(upload.getId()).orElse(null);
            assertTrue(actual.isVerified());

            handle.rollback();
        });
    }

    @Test
    public void testUpdateStatus() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            FileUpload upload = dao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 123L);
            assertNull(upload.getUploadedAt());
            assertNull(upload.getScannedAt());
            assertNull(upload.getScanResult());

            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            dao.updateStatus(upload.getId(), now, now, FileScanResult.CLEAN);
            FileUpload actual = dao.findById(upload.getId()).orElse(null);
            assertEquals(now, actual.getUploadedAt());
            assertEquals(now, actual.getScannedAt());
            assertEquals(FileScanResult.CLEAN, actual.getScanResult());

            handle.rollback();
        });
    }

    @Test
    public void testFindFileInfoByGuid() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            FileUpload upload = dao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 123L);

            FileInfo actual = dao.findFileInfoByGuid(upload.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getId(), actual.getUploadId());
            assertEquals(upload.getFileName(), actual.getFileName());
            assertEquals(upload.getFileSize(), actual.getFileSize());

            handle.rollback();
        });
    }
}

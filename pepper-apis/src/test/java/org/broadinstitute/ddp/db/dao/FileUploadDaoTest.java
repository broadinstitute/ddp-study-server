package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.instance.answer.FileInfo;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.files.FileUploadStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;
import org.testcontainers.shaded.org.apache.commons.lang.StringUtils;

public class FileUploadDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateAuthorized() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            long userId = testData.getUserId();
            FileUpload upload = dao.createAuthorized("guid", "blob", "mime", "file", 123, userId, userId);
            assertTrue(upload.getId() > 0);
            assertTrue(StringUtils.isNotBlank(upload.getGuid()));

            FileUpload actual = dao.findById(upload.getId()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getId(), actual.getId());

            actual = dao.findByGuid(upload.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getGuid(), actual.getGuid());
            assertEquals(FileUploadStatus.AUTHORIZED, actual.getStatus());
            assertEquals("first status equals creation time",
                    actual.getCreatedAt(), actual.getStatusChangedAt());

            handle.rollback();
        });
    }

    @Test
    public void testMarkUploaded() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            long userId = testData.getUserId();
            FileUpload upload = dao.createAuthorized("guid", "blob", "mime", "file", 123, userId, userId);
            assertEquals(FileUploadStatus.AUTHORIZED, upload.getStatus());

            var now = Instant.now();
            dao.markUploaded(upload.getId(), now);
            FileUpload actual = dao.findById(upload.getId()).orElse(null);
            assertEquals(FileUploadStatus.UPLOADED, actual.getStatus());
            assertEquals(now, actual.getUploadedAt());

            handle.rollback();
        });
    }

    @Test
    public void testFindFileInfoByGuid() {
        TransactionWrapper.useTxn(handle -> {
            var dao = handle.attach(FileUploadDao.class);

            long userId = testData.getUserId();
            FileUpload upload = dao.createAuthorized("guid", "blob", "mime", "file", 123, userId, userId);

            FileInfo actual = dao.findFileInfoByGuid(upload.getGuid()).orElse(null);
            assertNotNull(actual);
            assertEquals(upload.getId(), actual.getUploadId());
            assertEquals(upload.getFileName(), actual.getFileName());
            assertEquals(upload.getFileSize(), actual.getFileSize());

            handle.rollback();
        });
    }
}

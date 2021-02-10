package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class FileUploadServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    @Ignore("un-ignore to try locally")
    public void testTryAuthorize() {
        var service = FileUploadService.fromConfig(ConfigManager.getInstance().getConfig());
        var result = TransactionWrapper.withTxn(handle -> service
                .authorizeUpload(handle, testData.getUserId(), testData.getUserId(),
                        "prefix", "application/pdf", "filename.pdf", 50000, false));

        var guid = result.getFileUpload().getGuid();
        System.out.println("File upload guid: " + guid);

        var url = result.getSignedUrl().toString();
        System.out.println("File upload url: " + url);
    }

    @Test
    public void testAuthorizeUpload() throws MalformedURLException {
        var now = Instant.now();
        var dummyUpload = new FileUpload(1, "guid", "blob", "mime", "file", 123, 1, 1, false, now, null, null, null);
        var dummyUrl = new URL("https://datadonationplatform.org");
        var expectedMime = FileUploadService.DEFAULT_MIME_TYPE;
        var expectedMethod = HttpMethod.POST;

        var mockHandle = mock(Handle.class);
        var mockDao = mock(FileUploadDao.class);
        var mockClient = mock(GoogleBucketClient.class);
        var service = new FileUploadService(null, mockClient, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);

        doReturn(mockDao).when(mockHandle).attach(FileUploadDao.class);
        doReturn(dummyUpload).when(mockDao)
                .createAuthorized(any(), any(), any(), any(), anyLong(), anyLong(), anyLong());
        doReturn(dummyUrl).when(mockClient).generateSignedUrl(any(), eq("uploads"), startsWith("prefix/"),
                anyLong(), any(), eq(expectedMethod), argThat(map -> expectedMime.equals(map.get("Content-Type"))));

        var result = service.authorizeUpload(mockHandle, 1, 1, "prefix", null, "file", 123, true);

        assertNotNull(result);
        assertFalse(result.isExceededSize());
        assertNotNull(result.getFileUpload());
        assertNotNull(result.getSignedUrl());

        var returnedUpload = result.getFileUpload();
        assertEquals("guid", returnedUpload.getGuid());

        var returnedUrl = result.getSignedUrl();
        assertEquals(dummyUrl, returnedUrl);
    }

    @Test
    public void testAuthorizeUpload_exceededSize() {
        var service = new FileUploadService(null, null, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);
        var result = service.authorizeUpload(null, 1, 1, "prefix", "mime", "file", 1024, false);
        assertNotNull(result);
        assertTrue("should hit size limit", result.isExceededSize());
        assertNull(result.getFileUpload());
        assertNull(result.getSignedUrl());
    }

    @Test
    public void testVerifyUpload() {
        var now = Instant.now();
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var service = new FileUploadService(null, mockClient, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);

        long size = 100;
        doReturn(true).when(mockBlob).exists();
        doReturn(size).when(mockBlob).getSize();
        doReturn(now.toEpochMilli()).when(mockBlob).getCreateTime();
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", size, userId, userId);
            assertFalse(upload.isVerified());

            var result = service.verifyUpload(handle, userId, upload);
            assertNotNull(result);
            assertEquals(FileUploadService.VerifyResult.OK, result);

            var actual = fileDao.findById(upload.getId()).orElse(null);
            assertTrue(actual.isVerified());

            handle.rollback();
        });
    }

    @Test
    public void testVerifyUpload_alreadyVerifiedFilesAreNotCheckedAgain() {
        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);

            fileDao.markVerified(upload.getId());
            upload = fileDao.findById(upload.getId()).get();

            var service = new FileUploadService(null, null, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);
            var result = service.verifyUpload(handle, userId, upload);
            assertEquals(FileUploadService.VerifyResult.OK, result);

            handle.rollback();
        });
    }

    @Test
    public void testVerifyUpload_error() {
        var now = Instant.now();
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var service = new FileUploadService(null, mockClient, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);

            var result = service.verifyUpload(handle, userId + 1, upload);
            assertEquals(FileUploadService.VerifyResult.OWNER_MISMATCH, result);

            Mockito.reset(mockBlob);
            doReturn(false).when(mockBlob).exists();
            result = service.verifyUpload(handle, userId, upload);
            assertEquals(FileUploadService.VerifyResult.NOT_UPLOADED, result);

            Mockito.reset(mockBlob);
            doReturn(true).when(mockBlob).exists();
            doReturn(null).when(mockBlob).getCreateTime();
            result = service.verifyUpload(handle, userId, upload);
            assertEquals(FileUploadService.VerifyResult.NOT_UPLOADED, result);

            Mockito.reset(mockBlob);
            doReturn(true).when(mockBlob).exists();
            doReturn(now.toEpochMilli()).when(mockBlob).getCreateTime();
            doReturn(upload.getFileSize() + 1).when(mockBlob).getSize();
            result = service.verifyUpload(handle, userId, upload);
            assertEquals(FileUploadService.VerifyResult.SIZE_MISMATCH, result);

            var quarantinedUpload = new FileUpload(
                    upload.getId(), upload.getGuid(), upload.getBlobName(), upload.getMimeType(),
                    upload.getFileName(), upload.getFileSize(), upload.getOperatorUserId(),
                    upload.getParticipantUserId(), false, upload.getCreatedAt(), null,
                    Instant.now(), FileScanResult.INFECTED);
            result = service.verifyUpload(handle, userId, quarantinedUpload);
            assertEquals(FileUploadService.VerifyResult.QUARANTINED, result);

            handle.rollback();
        });
    }

    @Test
    public void testRemoveUnusedUploads() {
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var service = new FileUploadService(null, mockClient, "uploads", "scanned", "quarantine", 123, 5, 1L, null, 1);

        doReturn(true).when(mockBlob).exists();
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);
            assertFalse(upload.isVerified());

            Instant createdAt = Instant.parse("2021-01-01T00:00:00Z");
            Instant checkedAt = createdAt.plus(1L, ChronoUnit.MILLIS);

            assertEquals(1, handle.execute(
                    "update file_upload set created_at = ? where file_upload_id = ?",
                    createdAt, upload.getId()));

            int numRemoved = service.removeUnusedUploads(handle, checkedAt, null);
            assertEquals(1, numRemoved);
            assertFalse(fileDao.findById(upload.getId()).isPresent());

            handle.rollback();
        });
    }
}

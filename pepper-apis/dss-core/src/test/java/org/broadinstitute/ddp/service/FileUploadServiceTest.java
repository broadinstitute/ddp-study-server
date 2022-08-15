package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.FileUploadService.AuthorizeResultType.FILE_SIZE_EXCEEDS_MAXIMUM;
import static org.broadinstitute.ddp.service.FileUploadServiceTest.FileUploadSettingsForTest.createFileUploadSettings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
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
import java.util.Set;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.util.ConfigManager;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

public class FileUploadServiceTest extends TxnAwareBaseTest {
    private static long studyId;
    private static long userId;

    @BeforeClass
    public static void setup() {
        final var testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        studyId = testData.getStudyId();
        userId = testData.getUserId();
    }

    @Test
    @Ignore("un-ignore to try locally")
    public void testTryAuthorize() {
        long fileSize = 50000;
        var service = FileUploadService.fromConfig(ConfigManager.getInstance().getConfig());
        var result = TransactionWrapper.withTxn(handle -> service
                .authorizeUpload(handle, studyId, userId, userId, createFileUploadSettings(fileSize), GuidUtils.randomFileUploadGuid(),
                        "prefix/filename.pdf", "application/pdf", "filename.pdf", 50000, false));

        var guid = result.getFileUpload().getGuid();
        System.out.println("File upload guid: " + guid);

        var url = result.getSignedUrl().toString();
        System.out.println("File upload url: " + url);
    }

    @Test
    public void testAuthorizeUpload() throws MalformedURLException {
        var now = Instant.now();
        var dummyUpload = FileUpload.builder()
                .id(1L)
                .guid("guid")
                .studyId(1L)
                .operatorUserId(1L)
                .participantUserId(1L)
                .blobName("blob")
                .mimeType("mime")
                .fileName("file")
                .fileSize(123L)
                .isVerified(false)
                .createdAt(now)
                .build();
        var dummyUrl = new URL("https://datadonationplatform.org");
        var expectedMime = FileUploadService.DEFAULT_MIME_TYPE;
        var expectedMethod = HttpMethod.POST;

        var mockHandle = mock(Handle.class);
        var mockDao = mock(FileUploadDao.class);
        var mockClient = mock(GoogleBucketClient.class);
        var mockSendGrid = mock(SendGridClient.class);
        var service = new FileUploadService(mockSendGrid, null, mockClient, "uploads", "scanned", "quarantine", 5, 1L, null, 1);

        doReturn(mockDao).when(mockHandle).attach(FileUploadDao.class);
        doReturn(dummyUpload).when(mockDao)
                .createAuthorized(any(), anyLong(), anyLong(), anyLong(), any(), any(), any(), anyLong());
        doReturn(dummyUrl).when(mockClient).generateSignedUrl(any(), eq("uploads"), startsWith("prefix/"),
                anyLong(), any(), eq(expectedMethod), argThat(map -> expectedMime.equals(map.get("Content-Type"))));

        var result = service.authorizeUpload(mockHandle, 1L, 1L, 1L, createFileUploadSettings(123),
                GuidUtils.randomFileUploadGuid(), "prefix/file", null, "file", 123, true);

        assertNotNull(result);
        assertNotSame(result.getAuthorizeResultType(), FILE_SIZE_EXCEEDS_MAXIMUM);
        assertNotNull(result.getFileUpload());
        assertNotNull(result.getSignedUrl());

        var returnedUpload = result.getFileUpload();
        assertEquals("guid", returnedUpload.getGuid());

        var returnedUrl = result.getSignedUrl();
        assertEquals(dummyUrl, returnedUrl);
    }

    @Test
    public void testAuthorizeUpload_exceededSize() {
        var mockSendGrid = mock(SendGridClient.class);
        var service = new FileUploadService(mockSendGrid, null, null, "uploads", "scanned", "quarantine", 5, 1L, null, 1);
        var result = service.authorizeUpload(null, 1L, 1L, 1L, createFileUploadSettings(123),
                GuidUtils.randomFileUploadGuid(), "prefix/file", "mime", "file", 1024, false);
        assertNotNull(result);
        assertSame("should hit size limit", result.getAuthorizeResultType(), FILE_SIZE_EXCEEDS_MAXIMUM);
        assertNull(result.getFileUpload());
        assertNull(result.getSignedUrl());
    }

    @Test
    public void testVerifyUpload() {
        var now = Instant.now();
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var mockSendGrid = mock(SendGridClient.class);
        var service = new FileUploadService(mockSendGrid, null, mockClient, "uploads", "scanned", "quarantine", 5, 1L, null, 1);

        long size = 100;
        doReturn(true).when(mockBlob).exists();
        doReturn(size).when(mockBlob).getSize();
        doReturn(now.toEpochMilli()).when(mockBlob).getCreateTime();
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", size);
            assertFalse(upload.isVerified());

            var result = service.verifyUpload(handle, studyId, userId, upload);
            assertNotNull(result);
            assertEquals(FileUploadService.VerifyResult.OK, result);

            var actual = fileDao.findById(upload.getId()).orElse(null);
            assertNotNull(actual);
            assertTrue(actual.isVerified());

            handle.rollback();
        });
    }

    @Test
    public void testVerifyUpload_alreadyVerifiedFilesAreNotCheckedAgain() {
        TransactionWrapper.useTxn(handle -> {
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 100);

            fileDao.markVerified(upload.getId());
            upload = fileDao.findById(upload.getId()).orElseThrow();

            var mockSendGrid = mock(SendGridClient.class);
            var service = new FileUploadService(mockSendGrid, null, null, "uploads", "scanned", "quarantine", 5, 1L, null, 1);
            var result = service.verifyUpload(handle, studyId, userId, upload);
            assertEquals(FileUploadService.VerifyResult.OK, result);

            handle.rollback();
        });
    }

    @Test
    public void testVerifyUpload_error() {
        var now = Instant.now();
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var mockSendGrid = mock(SendGridClient.class);
        var service = new FileUploadService(mockSendGrid, null, mockClient, "uploads", "scanned", "quarantine", 5, 1L, null, 1);
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 100);

            var result = service.verifyUpload(handle, studyId, userId + 1, upload);
            assertEquals(FileUploadService.VerifyResult.OWNER_MISMATCH, result);

            result = service.verifyUpload(handle, studyId + 1, userId, upload);
            assertEquals(FileUploadService.VerifyResult.OWNER_MISMATCH, result);

            Mockito.reset(mockBlob);
            doReturn(false).when(mockBlob).exists();
            result = service.verifyUpload(handle, studyId, userId, upload);
            assertEquals(FileUploadService.VerifyResult.NOT_UPLOADED, result);

            Mockito.reset(mockBlob);
            doReturn(true).when(mockBlob).exists();
            doReturn(null).when(mockBlob).getCreateTime();
            result = service.verifyUpload(handle, studyId, userId, upload);
            assertEquals(FileUploadService.VerifyResult.NOT_UPLOADED, result);

            Mockito.reset(mockBlob);
            doReturn(true).when(mockBlob).exists();
            doReturn(now.toEpochMilli()).when(mockBlob).getCreateTime();
            doReturn(upload.getFileSize() + 1).when(mockBlob).getSize();
            result = service.verifyUpload(handle, studyId, userId, upload);
            assertEquals(FileUploadService.VerifyResult.SIZE_MISMATCH, result);

            var quarantinedUpload = upload.toBuilder()
                    .isVerified(false)
                    .uploadedAt(null)
                    .scanResult(FileScanResult.INFECTED)
                    .scannedAt(Instant.now())
                    .build();
            result = service.verifyUpload(handle, studyId, userId, quarantinedUpload);
            assertEquals(FileUploadService.VerifyResult.QUARANTINED, result);

            handle.rollback();
        });
    }

    @Test
    public void testRemoveUnusedUploads() {
        var mockBlob = mock(Blob.class);
        var mockClient = mock(GoogleBucketClient.class);
        var mockSendGrid = mock(SendGridClient.class);
        var service = new FileUploadService(mockSendGrid, null, mockClient, "uploads", "scanned", "quarantine", 5, 1L, null, 1);

        doReturn(true).when(mockBlob).exists();
        doReturn(mockBlob).when(mockClient).getBlob("uploads", "blob");

        TransactionWrapper.useTxn(handle -> {
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", studyId, userId, userId, "blob", "mime", "file", 100);
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

    static class FileUploadSettingsForTest implements FileUploadSettings {

        private final long maxFileSize;
        private final Set<String> mimeTypes;

        static FileUploadSettingsForTest createFileUploadSettings(long maxFileSize, Set<String> mimeTypes) {
            return new FileUploadSettingsForTest(maxFileSize, mimeTypes);
        }

        static FileUploadSettingsForTest createFileUploadSettings(long maxFileSize) {
            return createFileUploadSettings(maxFileSize, Set.of());
        }

        private FileUploadSettingsForTest(long maxFileSize, Set<String> mimeTypes) {
            this.maxFileSize = maxFileSize;
            this.mimeTypes = mimeTypes;
        }

        @Override
        public long getMaxFileSize() {
            return maxFileSize;
        }

        @Override
        public Set<String> getMimeTypes() {
            return mimeTypes;
        }
    }
}

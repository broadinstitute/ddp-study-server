package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.files.FileUploadStatus;
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
        var dummyUpload = new FileUpload(1, "guid", "blob", "mime", "file", 123, 1, 1,
                FileUploadStatus.AUTHORIZED, now, now, null);
        var dummyUrl = new URL("https://datadonationplatform.org");
        var expectedMime = FileUploadService.DEFAULT_MIME_TYPE;
        var expectedMethod = HttpMethod.POST;

        var mockHandle = mock(Handle.class);
        var mockDao = mock(FileUploadDao.class);
        var serviceSpy = spy(new FileUploadService(null, null, "bucket", 123, 5));

        doReturn(mockDao).when(mockHandle).attach(FileUploadDao.class);
        doReturn(dummyUpload).when(mockDao)
                .createAuthorized(any(), any(), any(), any(), anyInt(), anyLong(), anyLong());
        doReturn(dummyUrl).when(serviceSpy)
                .generateSignedUrl(startsWith("prefix/"), eq(expectedMime), eq(expectedMethod));

        var result = serviceSpy.authorizeUpload(mockHandle, 1, 1, "prefix", null, "file", 123, true);

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
        var service = new FileUploadService(null, null, "bucket", 123, 5);
        var result = service.authorizeUpload(null, 1, 1, "prefix", "mime", "file", 1024, false);
        assertNotNull(result);
        assertTrue("should hit size limit", result.isExceededSize());
        assertNull(result.getFileUpload());
        assertNull(result.getSignedUrl());
    }

    @Test
    public void testCheckAndSetUploadStatus() {
        var now = Instant.now();
        var blobMock = mock(Blob.class);
        var serviceSpy = spy(new FileUploadService(null, null, "bucket", 123, 5));

        doReturn(blobMock).when(serviceSpy).fetchBlob("blob");
        doReturn(true).when(blobMock).exists();
        doReturn(100L).when(blobMock).getSize();
        doReturn(now.toEpochMilli()).when(blobMock).getCreateTime();

        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);

            var result = serviceSpy.checkAndSetUploadStatus(handle, userId, upload);
            assertNotNull(result);
            assertEquals(FileUploadService.CheckResult.OK, result);

            var actual = fileDao.findById(upload.getId()).orElse(null);
            assertEquals(FileUploadStatus.UPLOADED, actual.getStatus());
            assertEquals("should match at millis precision",
                    now.truncatedTo(ChronoUnit.MILLIS), actual.getUploadedAt());

            handle.rollback();
        });
    }

    @Test
    public void testCheckAndSetUploadStatus_alreadyUploadedFilesAreNotCheckedAgain() {
        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);

            fileDao.markUploaded(upload.getId(), Instant.now());
            upload = fileDao.findById(upload.getId()).get();

            var service = new FileUploadService(null, null, "bucket", 123, 5);
            var result = service.checkAndSetUploadStatus(handle, userId, upload);
            assertEquals(FileUploadService.CheckResult.OK, result);

            handle.rollback();
        });
    }

    @Test
    public void testCheckAndSetUploadStatus_error() {
        var now = Instant.now();
        var blobMock = mock(Blob.class);
        var serviceSpy = spy(new FileUploadService(null, null, "bucket", 123, 5));
        doReturn(blobMock).when(serviceSpy).fetchBlob("blob");

        TransactionWrapper.useTxn(handle -> {
            long userId = testData.getUserId();
            var fileDao = handle.attach(FileUploadDao.class);
            var upload = fileDao.createAuthorized("guid", "blob", "mime", "file", 100, userId, userId);

            var result = serviceSpy.checkAndSetUploadStatus(handle, userId + 1, upload);
            assertEquals(FileUploadService.CheckResult.OWNER_MISMATCH, result);

            Mockito.reset(blobMock);
            doReturn(false).when(blobMock).exists();
            result = serviceSpy.checkAndSetUploadStatus(handle, userId, upload);
            assertEquals(FileUploadService.CheckResult.NOT_UPLOADED, result);

            Mockito.reset(blobMock);
            doReturn(true).when(blobMock).exists();
            doReturn(null).when(blobMock).getCreateTime();
            result = serviceSpy.checkAndSetUploadStatus(handle, userId, upload);
            assertEquals(FileUploadService.CheckResult.NOT_UPLOADED, result);

            Mockito.reset(blobMock);
            doReturn(true).when(blobMock).exists();
            doReturn(now.toEpochMilli()).when(blobMock).getCreateTime();
            doReturn(upload.getFileSize() + 1).when(blobMock).getSize();
            result = serviceSpy.checkAndSetUploadStatus(handle, userId, upload);
            assertEquals(FileUploadService.CheckResult.SIZE_MISMATCH, result);

            handle.rollback();
        });
    }
}

package org.broadinstitute.ddp.service;

import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.util.ConfigManager;
import org.junit.Test;

import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@Slf4j
public class FileDownloadServiceTest {

    @Test
    public void testGenerateSignedUrl() throws FileNotFoundException {
        var service = FileDownloadService.fromConfig(ConfigManager.getInstance().getConfig());
        URL signedUrl = service.getSignedURL("test_file.txt", "ddp-dev-file-scanned");
        assertNotNull(signedUrl);
        assertTrue(signedUrl.toString().contains("ddp-dev-file-scanned/test_file.txt"));
    }

    @Test
    public void testGenerateSignedUrlFileNotFound() throws FileNotFoundException {
        var service = FileDownloadService.fromConfig(ConfigManager.getInstance().getConfig());
        try {
            URL signedUrl = service.getSignedURL("test_file_invalid.txt", "ddp-dev-file-scanned");
        assertNotNull(signedUrl);
        } catch (FileNotFoundException e) {
            //all good
        }
    }

    @Test
    public void testGenerateDownloadUrl() throws MalformedURLException, FileNotFoundException {
        var dummyUrl = new URL("https://storage.googleapis.com/dev-bucket/test_file.txt");
        var expectedMethod = HttpMethod.GET;
        var mockClient = mock(GoogleBucketClient.class);
        var mockBlob = mock(Blob.class);
        var mockServiceAccountSigner = mock(ServiceAccountSigner.class);
        var service = new FileDownloadService(mockServiceAccountSigner, mockClient, 5, "dev-bucket");

        doReturn(mockBlob).when(mockClient).getBlob(any(), any());
        doReturn(true).when(mockBlob).exists();
        doReturn(dummyUrl).when(mockClient).generateSignedUrl(any(), any(), any(),
                anyLong(), any(), eq(expectedMethod), any());

        var returnedUrl = service.getSignedURL("test_file.txt", "dev-bucket");
        assertNotNull(returnedUrl);
        assertEquals(dummyUrl, returnedUrl);
    }

}

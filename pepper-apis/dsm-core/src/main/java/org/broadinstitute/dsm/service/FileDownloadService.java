package org.broadinstitute.dsm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.HttpMethod;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.dsm.statics.ApplicationConfigConstants;

@Slf4j
public class FileDownloadService {
    private final ServiceAccountSigner signer;
    private final GoogleBucketClient storageClient;
    private final int maxSignedUrlMins;


    public static FileDownloadService fromConfig(Config cfg) {
        String signerJson = ConfigUtil.toJson(cfg.getConfig(ApplicationConfigConstants.FILE_DOWNLOAD_CREDENTIALS));
        InputStream signerStream = new ByteArrayInputStream(signerJson.getBytes(StandardCharsets.UTF_8));
        ServiceAccountCredentials signerCredentials;
        try {
            signerCredentials = ServiceAccountCredentials.fromStream(signerStream);
        } catch (IOException e) {
            throw new DDPException("Could not get signer credentials", e);
        }

        GoogleCredentials bucketCredentials;
        boolean ensureDefault = false;
        bucketCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);
        if (bucketCredentials == null) {
            log.error("Could not get bucket credentials, defaulting to signer credentials");
            bucketCredentials = signerCredentials;
        }

        String projectId = cfg.getString(ApplicationConfigConstants.FILE_DOWNLOAD_PROJECT_ID);

        return new FileDownloadService(
                signerCredentials,
                new GoogleBucketClient(projectId, bucketCredentials),
                cfg.getInt(ApplicationConfigConstants.MAX_SIGN_URL_MIN));
    }

    public FileDownloadService(ServiceAccountSigner signer, GoogleBucketClient storageClient,
                               int maxSignedUrlMins) {
        this.signer = signer;
        this.storageClient = storageClient;
        this.maxSignedUrlMins = maxSignedUrlMins;
    }

    /**
     * Generating a signed URL for file download
     *
     * @param blobName   the name of the blob sent from frontend request
     * @param bucketName the name of the bucket sent from frontend request
     * @return URL
     */
    public URL getSignedURL(String blobName, String bucketName) {
        HttpMethod method = HttpMethod.GET;
        URL signedURL = storageClient.generateSignedUrl(
                signer, bucketName, blobName,
                maxSignedUrlMins, TimeUnit.MINUTES,
                method, new HashMap<>());

        return signedURL;
    }
}

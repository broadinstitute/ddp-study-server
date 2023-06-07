package org.broadinstitute.ddp.service;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.HttpMethod;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FileDownloadService {
    private final ServiceAccountSigner signer;
    private final GoogleBucketClient storageClient;
    private final int maxSignedUrlMins;
    private final String defaultDownloadBucket;

    //GCP Service Account for file download
    public static final String FILE_DOWNLOAD_CREDENTIALS = "fileDownload.signerServiceAccount";
    public static final String FILE_DOWNLOAD_PROJECT_ID = "fileDownload.signerServiceAccount.project_id";
    public static final String MAX_SIGN_URL_MIN = "fileDownload.maxSignedUrlMins";
    public static final String DEFAULT_DOWNLOAD_BUCKET = "fileDownload.defaultDownloadBucket";

    public static FileDownloadService fromConfig(Config cfg) {

        String signerJson = ConfigUtil.toJson(cfg.getConfig(FILE_DOWNLOAD_CREDENTIALS));
        InputStream signerStream = new ByteArrayInputStream(signerJson.getBytes(StandardCharsets.UTF_8));
        ServiceAccountCredentials signerCredentials;
        try {
            signerCredentials = ServiceAccountCredentials.fromStream(signerStream);
        } catch (IOException e) {
            throw new DDPException("Could not get signer credentials", e);
        }

        GoogleCredentials bucketCredentials;
        boolean ensureDefault = cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS);
        bucketCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);
        if (bucketCredentials == null) {
            log.error("Could not get bucket credentials, defaulting to signer credentials");
            bucketCredentials = signerCredentials;
        }

        String projectId = cfg.getString(FILE_DOWNLOAD_PROJECT_ID);

        return new FileDownloadService(
                signerCredentials,
                new GoogleBucketClient(projectId, bucketCredentials),
                cfg.getInt(MAX_SIGN_URL_MIN), cfg.getString(DEFAULT_DOWNLOAD_BUCKET));
    }

    public FileDownloadService(ServiceAccountSigner signer, GoogleBucketClient storageClient,
                               int maxSignedUrlMins, String defaultDownloadBucket) {
        this.signer = signer;
        this.storageClient = storageClient;
        this.maxSignedUrlMins = maxSignedUrlMins;
        this.defaultDownloadBucket = defaultDownloadBucket;
    }

    /**
     * Generating a signed URL for file download
     *
     * @param fileName   the name of the file
     * @param bucketName the name of the bucket
     * @return URL
     */
    public URL getSignedURL(String fileName, String bucketName) {
        HttpMethod method = HttpMethod.GET;
        URL signedURL = storageClient.generateSignedUrl(
                signer, StringUtils.isEmpty(bucketName) ? defaultDownloadBucket : bucketName, fileName,
                maxSignedUrlMins, TimeUnit.MINUTES,
                method, new HashMap<>());

        return signedURL;
    }
}

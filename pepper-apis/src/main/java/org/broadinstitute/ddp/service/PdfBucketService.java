package org.broadinstitute.ddp.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.typesafe.config.Config;
import org.apache.commons.io.IOUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.GoogleBucketUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PdfBucketService {

    private static final Logger LOG = LoggerFactory.getLogger(PdfBucketService.class);

    private Storage storage;
    private String bucketName;
    private boolean useCloudStorage;

    public static String getBlobName(String umbrellaGuid, String studyGuid, String userGuid, String pdfConfigName, String pdfVersionTag) {
        return String.format("%s/%s/%s_%s_%s.pdf", umbrellaGuid, studyGuid, userGuid, pdfConfigName, pdfVersionTag);
    }

    /**
     * Depending on given configuration, the service will use Google Bucket or local filesystem as backing storage for pdfs.
     *
     * @param cfg application configs
     */
    public PdfBucketService(Config cfg) {
        boolean useFilesystem = cfg.getBoolean(ConfigFile.PDF_ARCHIVE_USE_FILESYSTEM);
        if (useFilesystem) {
            LOG.error("Will use local filesystem for storing pdfs. Please double-check configs if this is not desired.");
            useCloudStorage = false;
        } else {
            boolean ensureDefault = cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS);
            GoogleCredentials googleCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);
            if (googleCredentials != null) {
                storage = GoogleBucketUtil.getStorage(googleCredentials, cfg.getString(ConfigFile.GOOGLE_PROJECT_ID));
                useCloudStorage = true;
            } else {
                throw new IllegalStateException("Need to have backing storage for pdfs!");
            }
        }
        bucketName = cfg.getString(ConfigFile.PDF_ARCHIVE_BUCKET);
    }

    public boolean isUseCloudStorage() {
        return useCloudStorage;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String sendPdfToBucket(String umbrellaGuid, String studyGuid, String userGuid,
                                  String pdfConfigName, String pdfVersionTag, InputStream contents) {
        String blobName = getBlobName(umbrellaGuid, studyGuid, userGuid, pdfConfigName, pdfVersionTag);
        sendPdfToBucket(blobName, contents);
        return blobName;
    }

    /**
     * Save given pdf to underlying storage.
     *
     * @param blobName the pdf blob name
     * @param contents the pdf blob contents
     */
    public void sendPdfToBucket(String blobName, InputStream contents) {
        if (useCloudStorage) {
            GoogleBucketUtil.uploadFile(storage, bucketName, blobName, "application/pdf", contents);
        } else {
            try {
                String tmpdir = System.getProperty("java.io.tmpdir");
                Path filepath = Paths.get(tmpdir, bucketName, blobName);
                filepath.toAbsolutePath().getParent().toFile().mkdirs();
                Files.write(filepath, IOUtils.toByteArray(contents));
                LOG.warn("Stored pdf to local filesystem temp directory: {}", filepath);
            } catch (IOException e) {
                throw new DDPException("Error while storing pdf " + blobName + " to local filesystem", e);
            }
        }
    }

    public Optional<InputStream> getPdfFromBucket(String umbrellaGuid, String studyGuid, String userGuid,
                                                  String pdfConfigName, String pdfVersionTag) {
        String blobName = getBlobName(umbrellaGuid, studyGuid, userGuid, pdfConfigName, pdfVersionTag);
        return getPdfFromBucket(blobName);
    }

    public String generateTempPdfBlobName(String baseName) {
        return GuidUtils.randomWithPrefix(baseName + "-", GuidUtils.UPPER_ALPHA_NUMERIC, 5) + ".pdf";
    }

    /**
     * Fetch pdf with given blob name from underlying storage.
     *
     * @param blobName the pdf blob name
     * @return pdf content, or empty if not found
     */
    public Optional<InputStream> getPdfFromBucket(String blobName) {
        if (useCloudStorage) {
            return GoogleBucketUtil.downloadFile(storage, bucketName, blobName);
        } else {
            try {
                String tmpdir = System.getProperty("java.io.tmpdir");
                Path filepath = Paths.get(tmpdir, bucketName, blobName);
                LOG.warn("Fetching pdf from local filesystem temp directory: {}", filepath);
                if (filepath.toFile().exists()) {
                    return Optional.of(Files.newInputStream(filepath));
                } else {
                    return Optional.empty();
                }
            } catch (IOException e) {
                throw new DDPException("Error while fetching pdf " + blobName + " from local filesystem", e);
            }
        }
    }
}

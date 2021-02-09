package org.broadinstitute.ddp.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.common.annotations.VisibleForTesting;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleBucketUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUploadService {

    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);

    private final ServiceAccountSigner signer;
    private final Storage storage;
    private final String uploadsBucket;
    private final String scannedBucket;
    private final long maxFileSizeBytes;
    private final int maxSignedUrlMins;

    public static FileUploadService fromConfig(Config cfg) {
        String signerJson = ConfigUtil.toJson(cfg.getConfig(ConfigFile.FileUploads.SIGNER_SERVICE_ACCOUNT));
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
            LOG.error("Could not get bucket credentials, defaulting to signer credentials");
            bucketCredentials = signerCredentials;
        }

        String projectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        Storage storage = GoogleBucketUtil.getStorage(bucketCredentials, projectId);

        return new FileUploadService(
                signerCredentials, storage,
                cfg.getString(ConfigFile.FileUploads.UPLOADS_BUCKET),
                cfg.getString(ConfigFile.FileUploads.SCANNED_BUCKET),
                cfg.getLong(ConfigFile.FileUploads.MAX_FILE_SIZE_BYTES),
                cfg.getInt(ConfigFile.FileUploads.MAX_SIGNED_URL_MINS));
    }

    public FileUploadService(ServiceAccountSigner signer, Storage storage,
                             String uploadsBucket, String scannedBucket,
                             long maxFileSizeBytes, int maxSignedUrlMins) {
        this.signer = signer;
        this.storage = storage;
        this.uploadsBucket = uploadsBucket;
        this.scannedBucket = scannedBucket;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxSignedUrlMins = maxSignedUrlMins;
    }

    public String getUploadsBucket() {
        return uploadsBucket;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    /**
     * Authorize a file upload by creating a file record and generating a signed URL.
     *
     * @param handle            the database handle
     * @param operatorUserId    the operator who instantiated this request
     * @param participantUserId the participant who will own the file
     * @param blobPrefix        a prefix to prepend to blob name, e.g. for organizational purposes
     * @param mimeType          the user-reported mime type
     * @param fileName          the user-reported name for the file
     * @param fileSize          the user-reported file size
     * @param resumable         whether to allow resumable upload
     * @return authorization result
     */
    public AuthorizeResult authorizeUpload(Handle handle, long operatorUserId, long participantUserId,
                                           String blobPrefix, String mimeType,
                                           String fileName, long fileSize, boolean resumable) {
        if (fileSize > maxFileSizeBytes) {
            return new AuthorizeResult(true, null, null);
        }

        blobPrefix = blobPrefix != null ? blobPrefix + "/" : "";
        mimeType = mimeType != null ? mimeType : DEFAULT_MIME_TYPE;
        HttpMethod method = resumable ? HttpMethod.POST : HttpMethod.PUT;
        String uploadGuid = GuidUtils.randomFileUploadGuid();
        String blobName = blobPrefix + uploadGuid;

        FileUpload upload = handle.attach(FileUploadDao.class).createAuthorized(
                uploadGuid, blobName, mimeType, fileName, fileSize, operatorUserId, participantUserId);
        URL signedURL = generateSignedUrl(blobName, mimeType, method);

        return new AuthorizeResult(false, upload, signedURL);
    }

    @VisibleForTesting
    URL generateSignedUrl(String blobName, String mimeType, HttpMethod method) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(uploadsBucket, blobName)).build();
        Map<String, String> headers = Map.of("Content-Type", mimeType);
        return storage.signUrl(blobInfo, maxSignedUrlMins, TimeUnit.MINUTES,
                Storage.SignUrlOption.signWith(signer),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(method),
                Storage.SignUrlOption.withExtHeaders(headers));
    }

    // Convenience helper to lock file upload before verifying.
    public Optional<VerifyResult> verifyUpload(Handle handle, long participantUserId, long uploadId) {
        return handle.attach(FileUploadDao.class)
                .findAndLockById(uploadId)
                .map(upload -> verifyUpload(handle, participantUserId, upload));
    }

    /**
     * Determine if file meets criteria, and update its status appropriately. E.g. file must be uploaded, matches
     * reported file size, and belong to the participant. Files already verified will not be checked again.
     *
     * <p>File upload should be "locked" to ensure its status doesn't change midway or file gets moved to a different
     * bucket. Since the file upload is locked, caller should commit transaction as soon as possible to unblock other
     * transactions waiting on the file upload row.
     *
     * @param handle            the database handle
     * @param participantUserId the participant who this upload will be assigned to
     * @param upload            the file upload
     * @return check result
     */
    public VerifyResult verifyUpload(Handle handle, long participantUserId, FileUpload upload) {
        if (participantUserId != upload.getParticipantUserId()) {
            return VerifyResult.OWNER_MISMATCH;
        }

        if (!upload.isVerified()) {
            // File scan usually finish within a minute of upload, and verification call from user should happen
            // relatively quick right after the upload, so it might be rare for it to be scanned already. But let's
            // check for this condition anyways.
            if (upload.getScannedAt() != null && upload.getScanResult() == FileScanResult.INFECTED) {
                return VerifyResult.QUARANTINED;
            }

            Blob blob = fetchBlob(upload.getBlobName(), upload.getScanResult());
            boolean exists = (blob != null && blob.exists());
            Long size = blob == null ? null : blob.getSize();

            if (!exists || blob.getCreateTime() == null) {
                return VerifyResult.NOT_UPLOADED;
            }

            if (size == null || size != upload.getFileSize()) {
                return VerifyResult.SIZE_MISMATCH;
            }

            handle.attach(FileUploadDao.class).markVerified(upload.getId());
            LOG.info("File upload {} is now marked as verified", upload.getGuid());
        } else {
            LOG.info("File upload {} was already verified", upload.getGuid());
        }

        return VerifyResult.OK;
    }

    @VisibleForTesting
    Blob fetchBlob(String blobName, FileScanResult scanResult) {
        String bucketName = uploadsBucket;
        if (scanResult == FileScanResult.CLEAN) {
            bucketName = scannedBucket;
        }
        return storage.get(BlobId.of(bucketName, blobName));
    }

    public enum VerifyResult {
        /**
         * File hasn't been uploaded yet.
         */
        NOT_UPLOADED,
        /**
         * File does not belong to participant.
         */
        OWNER_MISMATCH,
        /**
         * File was scanned already and found to be infected.
         */
        QUARANTINED,
        /**
         * File size does not match up.
         */
        SIZE_MISMATCH,
        /**
         * No issues found.
         */
        OK,
    }

    public static class AuthorizeResult {
        private boolean exceededSize;
        private FileUpload fileUpload;
        private URL signedUrl;

        public AuthorizeResult(boolean exceededSize, FileUpload fileUpload, URL signedUrl) {
            this.exceededSize = exceededSize;
            this.fileUpload = fileUpload;
            this.signedUrl = signedUrl;
        }

        public boolean isExceededSize() {
            return exceededSize;
        }

        public FileUpload getFileUpload() {
            return fileUpload;
        }

        public URL getSignedUrl() {
            return signedUrl;
        }
    }
}

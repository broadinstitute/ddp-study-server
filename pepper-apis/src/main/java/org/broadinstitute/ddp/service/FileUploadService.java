package org.broadinstitute.ddp.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
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
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.files.FileUploadStatus;
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
    private final String bucketName;
    private final int maxFileSizeBytes;
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
                cfg.getInt(ConfigFile.FileUploads.MAX_FILE_SIZE_BYTES),
                cfg.getInt(ConfigFile.FileUploads.MAX_SIGNED_URL_MINS));
    }

    public FileUploadService(ServiceAccountSigner signer, Storage storage, String bucketName,
                             int maxFileSizeBytes, int maxSignedUrlMins) {
        this.signer = signer;
        this.storage = storage;
        this.bucketName = bucketName;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.maxSignedUrlMins = maxSignedUrlMins;
    }

    public String getBucketName() {
        return bucketName;
    }

    public int getMaxFileSizeBytes() {
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
                                           String fileName, int fileSize, boolean resumable) {
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
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName)).build();
        Map<String, String> headers = Map.of("Content-Type", mimeType);
        return storage.signUrl(blobInfo, maxSignedUrlMins, TimeUnit.MINUTES,
                Storage.SignUrlOption.signWith(signer),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(method),
                Storage.SignUrlOption.withExtHeaders(headers));
    }

    /**
     * Determine if file meets criteria, and update its status appropriately. E.g. file must be uploaded, matches
     * reported file size, and belong to the participant. Files already marked uploaded will not be checked again.
     *
     * @param handle            the database handle
     * @param participantUserId the participant who this upload will be assigned to
     * @param upload            the file upload
     * @return check result
     */
    public CheckResult checkAndSetUploadStatus(Handle handle, long participantUserId, FileUpload upload) {
        if (participantUserId != upload.getParticipantUserId()) {
            return CheckResult.OWNER_MISMATCH;
        }

        if (upload.getStatus() == FileUploadStatus.AUTHORIZED) {
            // File upload haven't been checked yet, do it now.
            Blob blob = fetchBlob(upload.getBlobName());
            boolean exists = (blob != null && blob.exists());
            Long size = blob == null ? null : blob.getSize();

            if (!exists || blob.getCreateTime() == null) {
                return CheckResult.NOT_UPLOADED;
            }

            if (size == null || size != upload.getFileSize()) {
                return CheckResult.SIZE_MISMATCH;
            }

            var uploadTime = Instant.ofEpochMilli(blob.getCreateTime());
            handle.attach(FileUploadDao.class).markUploaded(upload.getId(), uploadTime);
            LOG.info("Marked file upload {} as uploaded with time {}", upload.getGuid(), uploadTime);
        } else {
            LOG.info("File upload {} was already marked uploaded at {} with status {}",
                    upload.getGuid(), upload.getUploadedAt(), upload.getStatus());
        }

        return CheckResult.OK;
    }

    @VisibleForTesting
    Blob fetchBlob(String blobName) {
        return storage.get(BlobId.of(bucketName, blobName));
    }

    public enum CheckResult {
        /**
         * File hasn't been uploaded yet.
         */
        NOT_UPLOADED,
        /**
         * File does not belong to participant.
         */
        OWNER_MISMATCH,
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

package org.broadinstitute.ddp.service;

import static org.broadinstitute.ddp.service.FileUploadService.AuthorizeResultType.FILE_SIZE_EXCEEDS_MAXIMUM;
import static org.broadinstitute.ddp.service.FileUploadService.AuthorizeResultType.MIME_TYPE_NOT_ALLOWED;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import com.typesafe.config.Config;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.client.SendGridClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.interfaces.FileUploadSettings;
import org.broadinstitute.ddp.model.files.FileScanResult;
import org.broadinstitute.ddp.model.files.FileUpload;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.jdbi.v3.core.Handle;

@Slf4j
public class FileUploadService {
    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";
    public static final int DEFAULT_BATCH_SIZE = 100;

    private final SendGridClient sendGridClient;
    private final ServiceAccountSigner signer;
    private final GoogleBucketClient storageClient;
    private final String uploadsBucket;
    private final String scannedBucket;
    private final String quarantineBucket;
    private final int maxSignedUrlMins;
    private final long removalExpireTime;
    private final TimeUnit removalExpireUnit;
    private final int removalBatchSize;

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
            log.error("Could not get bucket credentials, defaulting to signer credentials");
            bucketCredentials = signerCredentials;
        }

        String projectId = cfg.getString(ConfigFile.GOOGLE_PROJECT_ID);
        int removalBatchSize = cfg.getInt(ConfigFile.FileUploads.REMOVAL_BATCH_SIZE);
        long removalExpireTime = cfg.getLong(ConfigFile.FileUploads.REMOVAL_EXPIRE_TIME);
        TimeUnit removalExpireUnit = TimeUnit.valueOf(cfg.getString(ConfigFile.FileUploads.REMOVAL_EXPIRE_UNIT));

        var apiKey = cfg.getString(ConfigFile.SENDGRID_API_KEY);
        var connectProxy = ConfigUtil.getStrIfPresent(cfg, ConfigFile.Sendgrid.PROXY);
        return new FileUploadService(
                new SendGridClient(apiKey, connectProxy),
                signerCredentials,
                new GoogleBucketClient(projectId, bucketCredentials),
                cfg.getString(ConfigFile.FileUploads.UPLOADS_BUCKET),
                cfg.getString(ConfigFile.FileUploads.SCANNED_BUCKET),
                cfg.getString(ConfigFile.FileUploads.QUARANTINE_BUCKET),
                cfg.getInt(ConfigFile.FileUploads.MAX_SIGNED_URL_MINS),
                removalExpireTime, removalExpireUnit, removalBatchSize);
    }

    public FileUploadService(SendGridClient sendGridClient, ServiceAccountSigner signer, GoogleBucketClient storageClient,
                             String uploadsBucket, String scannedBucket, String quarantineBucket,
                             int maxSignedUrlMins, long removalExpireTime, TimeUnit removalExpireUnit, int removalBatchSize) {
        this.signer = signer;
        this.sendGridClient = sendGridClient;
        this.storageClient = storageClient;
        this.uploadsBucket = uploadsBucket;
        this.scannedBucket = scannedBucket;
        this.quarantineBucket = quarantineBucket;
        this.maxSignedUrlMins = maxSignedUrlMins;
        this.removalExpireTime = removalExpireTime;
        this.removalExpireUnit = removalExpireUnit;
        this.removalBatchSize = removalBatchSize;
        if (removalExpireUnit == TimeUnit.MICROSECONDS || removalExpireUnit == TimeUnit.NANOSECONDS) {
            throw new IllegalArgumentException("Granularity of time unit is not supported: " + removalExpireUnit);
        }
    }

    public String getUploadsBucket() {
        return uploadsBucket;
    }

    /**
     * Get name of bucket that hosts the given file upload.
     *
     * @param upload the file upload
     * @return the bucket name
     */
    public String getBucketForUpload(FileUpload upload) {
        if (upload.getScanResult() == FileScanResult.CLEAN) {
            return scannedBucket;
        } else if (upload.getScanResult() == FileScanResult.INFECTED) {
            return quarantineBucket;
        } else {
            return uploadsBucket;
        }
    }

    /**
     * Authorize a file upload by creating a file record and generating a signed URL.
     *
     * @param handle            the database handle
     * @param studyId           the study to authorize upload for
     * @param operatorUserId    the operator who instantiated this request
     * @param participantUserId the participant who will own the file
     * @param fileUploadSettings file upload parameters
     * @param blobPath          a cloud path of the blob
     * @param mimeType          the user-reported mime type
     * @param fileName          the user-reported name for the file
     * @param fileSize          the user-reported file size
     * @param resumable         whether to allow resumable upload
     * @return authorization result
     */
    public AuthorizeResult authorizeUpload(Handle handle, long studyId, long operatorUserId, long participantUserId,
                                           FileUploadSettings fileUploadSettings, String fileGuid,
                                           String blobPath, String mimeType,
                                           String fileName, long fileSize, boolean resumable) {
        if (fileSize > fileUploadSettings.getMaxFileSize()) {
            return new AuthorizeResult(FILE_SIZE_EXCEEDS_MAXIMUM, null, null, fileUploadSettings);
        }
        if (mimeType != null && fileUploadSettings.getMimeTypes() != null && !fileUploadSettings.getMimeTypes().isEmpty()
                && !fileUploadSettings.getMimeTypes().contains(mimeType)) {
            return new AuthorizeResult(MIME_TYPE_NOT_ALLOWED, null, null, fileUploadSettings);
        }

        mimeType = mimeType != null ? mimeType : DEFAULT_MIME_TYPE;

        HttpMethod method = resumable ? HttpMethod.POST : HttpMethod.PUT;

        FileUpload upload = handle.attach(FileUploadDao.class).createAuthorized(
                fileGuid, studyId, operatorUserId, participantUserId,
                blobPath, mimeType, fileName, fileSize);
        Map<String, String> headers = Map.of("Content-Type", mimeType);
        URL signedURL = storageClient.generateSignedUrl(
                signer, uploadsBucket, blobPath,
                maxSignedUrlMins, TimeUnit.MINUTES,
                method, headers);

        return new AuthorizeResult(AuthorizeResultType.OK, upload, signedURL, fileUploadSettings);
    }

    // Convenience helper to lock file upload before verifying.
    public Optional<VerifyResult> verifyUpload(Handle handle, long studyId, long participantUserId, long uploadId) {
        return handle.attach(FileUploadDao.class)
                .findAndLockById(uploadId)
                .map(upload -> verifyUpload(handle, studyId, participantUserId, upload));
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
     * @param studyId           the study that participant is in
     * @param participantUserId the participant who this upload will be associated to
     * @param upload            the file upload
     * @return check result
     */
    public VerifyResult verifyUpload(Handle handle, long studyId, long participantUserId, FileUpload upload) {
        if (studyId != upload.getStudyId() || participantUserId != upload.getParticipantUserId()) {
            return VerifyResult.OWNER_MISMATCH;
        }

        if (!upload.isVerified()) {
            // File scan usually finish within a minute of upload, and verification call from user should happen
            // relatively quick right after the upload, so it might be rare for it to be scanned already. But let's
            // check for this condition anyways.
            if (upload.getScannedAt() != null && upload.getScanResult() == FileScanResult.INFECTED) {
                return VerifyResult.QUARANTINED;
            }

            String bucket = getBucketForUpload(upload);
            Blob blob = storageClient.getBlob(bucket, upload.getBlobName());
            boolean exists = (blob != null && blob.exists());
            Long size = blob == null ? null : blob.getSize();

            if (!exists || blob.getCreateTime() == null) {
                return VerifyResult.NOT_UPLOADED;
            }

            if (size == null || size != upload.getFileSize()) {
                return VerifyResult.SIZE_MISMATCH;
            }

            handle.attach(FileUploadDao.class).markVerified(upload.getId());
            log.info("File upload {} is now marked as verified", upload.getGuid());
        } else {
            log.info("File upload {} was already verified", upload.getGuid());
        }

        return VerifyResult.OK;
    }

    // Convenience helper to run removal using the service's configured settings.
    public int removeUnusedUploads(Handle handle) {
        log.info("Starting removal of unused file uploads older than {} {}", removalExpireTime, removalExpireUnit);
        long duration = removalExpireUnit.toMillis(removalExpireTime);
        Instant olderThanTimestamp = Instant.now().minusMillis(duration);
        return removeUnusedUploads(handle, olderThanTimestamp, removalBatchSize);
    }

    /**
     * Remove file uploads that are older than the given timestamp and is not used. And unused file upload is one that
     * is either not verified or is not associated with any file answers.
     *
     * @param handle             the database handle
     * @param olderThanTimestamp look for authorized uploads older than this timestamp
     * @param removalBatchSize   optional, will use the default batch size if not provided
     * @return number of file uploads removed
     */
    public int removeUnusedUploads(Handle handle, Instant olderThanTimestamp, Integer removalBatchSize) {
        var uploadDao = handle.attach(FileUploadDao.class);
        Set<Long> uploadIdsToDelete = new HashSet<>();
        int offset = 0;

        int batchSize = removalBatchSize != null ? removalBatchSize : DEFAULT_BATCH_SIZE;
        while (true) {
            ArrayDeque<FileUpload> queue;
            try (var stream = uploadDao.findUnverifiedOrUnassociatedUploads(olderThanTimestamp, offset, batchSize)) {
                queue = stream.collect(Collectors.toCollection(ArrayDeque::new));
            }
            if (queue.isEmpty()) {
                break;
            }

            int numFound = queue.size();
            while (!queue.isEmpty()) {
                FileUpload upload = queue.remove();
                String bucket = getBucketForUpload(upload);
                Blob blob = storageClient.getBlob(bucket, upload.getBlobName());
                if (blob != null && blob.exists()) {
                    try {
                        storageClient.deleteBlob(blob);
                        log.info("Deleted blob {} for unused file upload {}", blob.getBlobId(), upload.getGuid());
                    } catch (Exception e) {
                        log.error("Unable to delete file {} from bucket {}, skipping removal of unused file upload {}",
                                upload.getBlobName(), bucket, upload.getGuid());
                        continue;
                    }
                } else if (upload.getUploadedAt() != null) {
                    log.error("Unable to locate file {} in bucket {}, skipping removal of unused file upload {}",
                            upload.getBlobName(), bucket, upload.getGuid());
                    continue;
                } else {
                    // Nothing to do. Actual file is not uploaded to bucket.
                }
                uploadIdsToDelete.add(upload.getId());
            }

            offset += numFound;
        }

        uploadDao.deleteByIds(uploadIdsToDelete);
        log.info("Removed {} unused file uploads", uploadIdsToDelete.size());
        return uploadIdsToDelete.size();
    }

    public void sendNotifications(final Handle handle) {
        StreamEx.of(handle.attach(FileUploadDao.class).findWithoutSentNotification())
                .groupingBy(FileUpload::getStudyId)
                .forEach((studyId, fileUploads) -> sendNotifications(handle, studyId, fileUploads));
    }

    private void sendNotifications(final Handle handle, final Long studyId, final List<FileUpload> fileUploads) {
        final var study = handle.attach(JdbiUmbrellaStudy.class).findById(studyId);
        if (StringUtils.isBlank(study.getNotificationEmail())) {
            handle.attach(FileUploadDao.class).setNotificationSentByStudyId(studyId);
            log.warn("Study {} doesn't have an e-mail for notifications", study.getGuid());
            return;
        }

        if (study.getNotificationMailTemplateId() == null) {
            handle.attach(FileUploadDao.class).setNotificationSentByStudyId(studyId);
            log.warn("Study {} doesn't have an e-mail template for notifications", study.getGuid());
            return;
        }

        StreamEx.of(fileUploads)
                .groupingBy(FileUpload::getParticipantUserId)
                .forEach((participantId, uploads) -> sendNotification(handle, study, participantId, uploads));

        log.info("Notifications sent for {} study", study.getGuid());
    }

    private void sendNotification(final Handle handle,
                                  final StudyDto study,
                                  final Long participantId,
                                  final List<FileUpload> fileUploads) {
        final var user = handle.attach(UserDao.class).findUserById(participantId);

        final var result = sendGridClient.sendMail(
                FileUploadNotificationEmailFactory.create(study, user.map(User::getHruid).orElse(""), fileUploads));

        if (result.hasFailure()) {
            String message;
            if (result.hasError()) {
                message = String.format("Failed to send file upload notification email: %s.", result.getError());
            } else {
                message = String.format("Failed to send file upload notification email due to an unknown error.");
            }

            log.error("{}", message, result.getThrown());
            return;
        }

        handle.attach(FileUploadDao.class).setNotificationSentByFileUploadIds(StreamEx.of(fileUploads).map(FileUpload::getId).toList());
        log.info("A user #{} uploaded {} files in terms of {} study", participantId, fileUploads.size(), study.getGuid());
    }

    public enum VerifyResult {
        /**
         * File hasn't been uploaded yet.
         */
        NOT_UPLOADED,
        /**
         * File does not belong to participant or the study.
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

    public enum AuthorizeResultType {
        FILE_SIZE_EXCEEDS_MAXIMUM,
        MIME_TYPE_NOT_ALLOWED,
        OK
    }

    @Value
    @AllArgsConstructor
    public static class AuthorizeResult {
        AuthorizeResultType authorizeResultType;
        FileUpload fileUpload;
        URL signedUrl;
        FileUploadSettings fileUploadSettings;
    }
}

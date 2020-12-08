package org.broadinstitute.ddp.service;

import java.net.URL;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.typesafe.config.Config;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.model.fileupload.FileUpload;
import org.broadinstitute.ddp.model.fileupload.FileUploadStatus;
import org.broadinstitute.ddp.util.GoogleBucketUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUploadService {

    private static final Logger LOG = LoggerFactory.getLogger(FileUploadService.class);

    private static final long URL_VALID_TIME_MINUTES = 5;

    private final Storage storage;
    private final String bucketName;

    /**
     * Service initialization
     *
     * @param cfg application configs
     */
    public FileUploadService(Config cfg) {
        boolean ensureDefault = cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS);
        GoogleCredentials googleCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);
        if (googleCredentials != null) {
            storage = GoogleBucketUtil.getStorage(googleCredentials, cfg.getString(ConfigFile.GOOGLE_PROJECT_ID));
        } else {
            throw new IllegalStateException("Need to have backing storage for uploads!");
        }
        bucketName = cfg.getString(ConfigFile.FILE_UPLOAD_BUCKET);
    }

    public String getBucketName() {
        return bucketName;
    }

    public Storage getStorage() {
        return storage;
    }

    public URL getSignedURLForUpload(Handle handle, String fileUploadGuid, String studyGuid, String activityCode,
                                     String activityInstanceGuid, String answerGuid, String filename,
                                     Long fileSize, Date fileCreationTime, String mimeType) {
        String bucketFilename = generateName(studyGuid, activityCode, activityInstanceGuid, answerGuid, fileUploadGuid, filename);
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName,
                bucketFilename)).build();

        handle.attach(FileUploadDao.class).insertFileUpload(fileUploadGuid, bucketFilename, Instant.now().toEpochMilli(), filename,
                fileSize, FileUploadStatus.AUTHORIZED, fileCreationTime);

        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", mimeType);
        return storage.signUrl(blobInfo, URL_VALID_TIME_MINUTES, TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders));
    }

    public boolean validateUpload(Handle handle, String guid) {
        FileUploadDao fileUploadDao = handle.attach(FileUploadDao.class);
        FileUpload fileUpload = fileUploadDao.getFileUploadByGuid(guid)
                .orElseThrow(() -> new DaoException("Could not find file upload with guid " + guid));
        boolean valid = true;
        //TODO: validate bucket contents
        if (valid) {
            fileUploadDao.setStatus(fileUpload.getFileUploadGuid(), FileUploadStatus.VERIFIED);
        }
        return valid;
    }

    private static String generateName(String studyGuid, String activityCode, String activityInstanceGuid,
                                       String answerGuid, String fileUploadGuid, String filename) {
        String name;
        if (activityCode != null && activityInstanceGuid != null && answerGuid != null) {
            name = String.format("%s/%s/%s/%s/%s_%s", studyGuid, activityCode, activityInstanceGuid,
                    answerGuid, fileUploadGuid, filename);
        } else {
            name = String.format("%s/%s_%s", studyGuid, fileUploadGuid, filename);
        }
        return name;
    }
}

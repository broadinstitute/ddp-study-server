package org.broadinstitute.ddp.service;

import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.typesafe.config.Config;
import org.apache.commons.collections.CollectionUtils;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.FileUploadDao;
import org.broadinstitute.ddp.exception.DDPException;
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
    private final boolean useBucket;

    /**
     * Service initialization
     *
     * @param cfg application configs
     */
    public FileUploadService(Config cfg) {
        useBucket = cfg.getBoolean(ConfigFile.FILE_UPLOAD_USE_BUCKET);
        if (useBucket) {
            bucketName = cfg.getString(ConfigFile.FILE_UPLOAD_BUCKET);
            boolean ensureDefault = cfg.getBoolean(ConfigFile.REQUIRE_DEFAULT_GCP_CREDENTIALS);
            GoogleCredentials googleCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);
            if (googleCredentials != null) {
                storage = GoogleBucketUtil.getStorage(googleCredentials, cfg.getString(ConfigFile.GOOGLE_PROJECT_ID));
            } else {
                throw new IllegalStateException("Need to have backing storage for uploads!");
            }
        } else {
            storage = null;
            bucketName = null;
            LOG.error("Bucket usage is disabled - service has limited capabilities.");
        }
    }

    public String getBucketName() {
        return bucketName;
    }

    public Storage getStorage() {
        return storage;
    }

    public URL getSignedURLForUpload(Handle handle, String fileUploadGuid, String studyGuid,
                                     String activityInstanceGuid, String answerGuid, String filename,
                                     Long fileSize, String mimeType, HttpMethod httpMethod) {
        if (!useBucket) {
            throw new DDPException("Server is not configured to use bucket");
        }
        String bucketFilename = generateName(studyGuid, activityInstanceGuid, answerGuid, fileUploadGuid, filename);
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName,
                bucketFilename)).build();

        handle.attach(FileUploadDao.class).insertFileUpload(fileUploadGuid, bucketFilename, Instant.now(), filename,
                fileSize, FileUploadStatus.AUTHORIZED);

        Map<String, String> extensionHeaders = new HashMap<>();
        extensionHeaders.put("Content-Type", mimeType);
        return storage.signUrl(blobInfo, URL_VALID_TIME_MINUTES, TimeUnit.MINUTES,
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(httpMethod),
                Storage.SignUrlOption.withExtHeaders(extensionHeaders));
    }

    public Long validateUploadAndGetId(Handle handle, String guid) {
        FileUploadDao fileUploadDao = handle.attach(FileUploadDao.class);
        FileUpload fileUpload = fileUploadDao.getFileUploadByGuid(guid)
                .orElseThrow(() -> new DaoException("Could not find file upload with guid " + guid));
        if (useBucket) {
            Long bucketFileSize;
            Long bucketFileCreateTime;
            Page<Blob> page = storage.list(bucketName, Storage.BlobListOption.prefix(fileUpload.getBucketFileUrl()));
            Iterator<Blob> iterator = page.getValues().iterator();
            if (iterator.hasNext()) {
                Blob blob = iterator.next();
                bucketFileSize = blob.getSize();
                bucketFileCreateTime = blob.getCreateTime();
                if (bucketFileSize != null && bucketFileSize.equals(fileUpload.getFileSize())) {
                    fileUploadDao.setVerified(fileUpload.getFileUploadGuid(), bucketFileCreateTime);
                    return fileUpload.getId();
                }
            }
        } else {
            return fileUpload.getId();
        }
        return null;
    }

    public Map<FileUpload, InputStream> getFilesByIds(Handle handle, Collection<Long> ids) {
        Map<FileUpload, InputStream> result = new HashMap<>();
        List<FileUpload> fileUploads = handle.attach(FileUploadDao.class).getFileUploadsByIds(ids);
        fileUploads.forEach(fileUpload -> result.put(fileUpload,
                useBucket && FileUploadStatus.VERIFIED.equals(fileUpload.getStatus()) ? GoogleBucketUtil
                        .downloadFile(storage, bucketName, fileUpload.getBucketFileUrl()).orElseThrow(() -> {
                            String msg = String.format("File %s not found in bucket %s", fileUpload.getFileUploadGuid(), bucketName);
                            throw new DDPException(msg);
                        }) : null));
        return result;
    }

    public void removeFilesByIds(Handle handle, Collection<Long> ids) {
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        FileUploadDao fileUploadDao = handle.attach(FileUploadDao.class);
        List<FileUpload> fileUploads = fileUploadDao.getFileUploadsByIds(ids);
        for (FileUpload fileUpload : fileUploads) {
            if (!useBucket || GoogleBucketUtil.removeFile(storage, bucketName, fileUpload.getBucketFileUrl())) {
                fileUploadDao.removeFileUploadById(fileUpload.getId());
            }
        }
    }

    private static String generateName(String studyGuid, String activityInstanceGuid,
                                       String answerGuid, String fileUploadGuid, String filename) {
        String name;
        if (activityInstanceGuid != null && answerGuid != null) {
            name = String.format("%s/%s/%s/%s_%s", studyGuid, activityInstanceGuid,
                    answerGuid, fileUploadGuid, filename);
        } else {
            name = String.format("%s/%s_%s", studyGuid, fileUploadGuid, filename);
        }
        return name;
    }
}

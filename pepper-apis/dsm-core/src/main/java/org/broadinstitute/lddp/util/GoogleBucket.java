package org.broadinstitute.lddp.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import com.google.api.gax.paging.Page;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GoogleBucket {

    private static final Logger logger = LoggerFactory.getLogger(GoogleBucket.class);

    public static String uploadFile(String googleCredentials, @NonNull String googleCloudId, @NonNull String bucketName,
                                    @NonNull String fileName, @NonNull InputStream inputStream) {
        logger.info("Upload file to bucket now");
        try {
            Storage storage = getStorage(googleCredentials, googleCloudId);

            Bucket bucket = getBucketByName(storage, bucketName);

            if (bucket != null) {
                Blob blob = bucket.create(fileName, inputStream);
                if (blob != null) {
                    return fileName;
                }
            } else {
                throw new RuntimeException("Bucket not found.");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Failed to upload file into bucket.", ex);
        }
        return null;
    }

    public static boolean bucketExists(String googleCredentials, @NonNull String googleCloudId, @NonNull String bucketName)
            throws Exception {
        boolean exists = false;
        try {
            Storage storage = getStorage(googleCredentials, googleCloudId);

            Bucket bucket = getBucketByName(storage, bucketName);

            if (bucket != null) {
                exists = true;
            }
        } catch (Exception ex) {
            throw new RuntimeException("An error occurred searching for bucket: " + bucketName, ex);
        }
        return exists;
    }

    public static byte[] downloadFile(String googleCredentials, @NonNull String googleCloudId, @NonNull String bucketName,
                                      @NonNull String fileName) throws Exception {
        logger.info("Download file from bucket now");
        try {
            Storage storage = getStorage(googleCredentials, googleCloudId);

            BlobId blobId = BlobId.of(bucketName, fileName);
            Blob blob = storage.get(blobId);
            if (blob == null) {
                logger.warn("File not found " + fileName);
            } else {
                return blob.getContent();
            }
        } catch (Exception ex) {
            throw new RuntimeException("An error occurred downloading file from bucket." + bucketName, ex);
        }
        return null;
    }

    public static boolean deleteFile(String googleCredentials, @NonNull String googleCloudId, @NonNull String bucketName,
                                     @NonNull String fileName) {
        logger.info("Deleting file from bucket now " + fileName);
        try {
            Storage storage = getStorage(googleCredentials, googleCloudId);

            BlobId blobId = BlobId.of(bucketName, fileName);
            boolean deleted = storage.delete(blobId);
            if (deleted) {
                logger.info("File deleted " + fileName);
                return true;
            } else {
                logger.error("File not deleted " + fileName);
            }
        } catch (Exception ex) {
            logger.error("Failed to delete file from bucket", ex);
        }
        return false;
    }

    public static Storage getStorage(String googleCredentials, @NonNull String googleCloudId) throws Exception {
        GoogleCredentials credentials = null;
        if (StringUtils.isNotBlank(googleCredentials)) {
            credentials = GoogleCredentials.fromStream(new FileInputStream(googleCredentials))
                    .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        }
        StorageOptions.Builder storageBuilder = StorageOptions.newBuilder().setProjectId(googleCloudId);
        if (credentials != null) {
            storageBuilder.setCredentials(credentials);
        }
        return storageBuilder.build().getService();
    }

    public static Bucket getBucketByName(@NonNull Storage storage, @NonNull String bucketName) {
        Bucket foundBucket = null;

        for (Bucket bucket : storage.list().iterateAll()) {
            if (bucket.getName().equals(bucketName)) {
                foundBucket = bucket;
                break;
            }
        }
        return foundBucket;
    }

    public static List<String> getFiles(String googleCredentials, @NonNull String googleCloudId, @NonNull String bucketName)
            throws Exception {
        List<String> fileNames = new ArrayList<>();
        Storage storage = GoogleBucket.getStorage(googleCredentials, googleCloudId);
        Page<Blob> blobs = storage.list(bucketName);
        for (Blob blob : blobs.iterateAll()) {
            fileNames.add(blob.getName());
        }
        return fileNames;
    }
}

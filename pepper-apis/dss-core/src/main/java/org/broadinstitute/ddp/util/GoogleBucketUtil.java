package org.broadinstitute.ddp.util;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.util.Optional;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.common.collect.Lists;
import lombok.NonNull;
import org.broadinstitute.ddp.exception.DDPException;

public class GoogleBucketUtil {

    public static Storage getStorage(@NonNull GoogleCredentials googleCredentials, @NonNull String googleCloudId) {
        return StorageOptions.newBuilder()
                .setCredentials(googleCredentials)
                .setProjectId(googleCloudId)
                .build()
                .getService();
    }

    @Deprecated
    static Storage getStorage(@NonNull String googleCredentials, @NonNull String googleCloudId) throws Exception {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(googleCredentials))
                .createScoped(Lists.newArrayList("https://www.googleapis.com/auth/cloud-platform"));
        return getStorage(credentials, googleCloudId);
    }

    /**
     * Store given file to bucket.
     *
     * @param storage     the storage service
     * @param bucketName  the bucket name
     * @param filename    the file (blob) name
     * @param contentType the content (mime) type
     * @param contents    the file contents
     * @return information about the newly stored file
     */
    public static Blob uploadFile(Storage storage, String bucketName, String filename, String contentType, InputStream contents) {
        Bucket bucket = storage.get(bucketName);
        if (bucket == null) {
            throw new DDPException("Could not find bucket with name " + bucketName);
        }

        try {
            return bucket.create(filename, contents, contentType);
        } catch (Exception e) {
            String msg = String.format("Error while uploading file %s to bucket %s", filename, bucketName);
            throw new DDPException(msg, e);
        }
    }

    /**
     * Fetch the contents of a file from bucket.
     *
     * @param storage    the storage service
     * @param bucketName the bucket name
     * @param filename   the file (blob) name
     * @return content stream, or empty if not found
     */
    public static Optional<InputStream> downloadFile(Storage storage, String bucketName, String filename) {
        Blob blob;
        try {
            blob = storage.get(bucketName, filename);
        } catch (Exception e) {
            String msg = String.format("Error while downloading file %s from bucket %s", filename, bucketName);
            throw new DDPException(msg, e);
        }

        if (blob != null) {
            return Optional.of(Channels.newInputStream(blob.reader()));
        } else {
            return Optional.empty();
        }
    }
}


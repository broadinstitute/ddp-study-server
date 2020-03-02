package org.broadinstitute.ddp.client;

import com.google.auth.Credentials;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.StorageOptions;

/**
 * A client wrapper around Google Bucket services.
 */
public class GoogleBucketClient {

    private final StorageOptions storage;

    public GoogleBucketClient(String gcpProjectId, Credentials credentials) {
        this(StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(gcpProjectId)
                .build());
    }

    public GoogleBucketClient(StorageOptions storage) {
        this.storage = storage;
    }

    public Bucket getBucket(String bucketName) {
        return storage.getService().get(bucketName);
    }
}

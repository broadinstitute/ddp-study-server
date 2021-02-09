package org.broadinstitute.ddp.client;

import com.google.auth.Credentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.broadinstitute.ddp.exception.DDPException;

/**
 * A client wrapper around Google Bucket services.
 */
public class GoogleBucketClient {

    private final Storage storage;

    public GoogleBucketClient(String gcpProjectId, Credentials credentials) {
        this(StorageOptions.newBuilder()
                .setCredentials(credentials)
                .setProjectId(gcpProjectId)
                .build().getService());
    }

    public GoogleBucketClient(Storage storage) {
        this.storage = storage;
    }

    public Bucket getBucket(String bucketName) {
        return storage.get(bucketName);
    }

    public Blob getBlob(String bucketName, String fileName) {
        return storage.get(bucketName, fileName);
    }

    public Blob copyBlob(Blob sourceBlob, String destBucketName, String destFileName) {
        try {
            return sourceBlob.copyTo(destBucketName, destFileName).getResult();
        } catch (Exception e) {
            throw new DDPException("Failed to copy source blob " + sourceBlob.getBlobId()
                    + " to destination bucket " + destBucketName);
        }
    }

    public Blob moveBlob(Blob sourceBlob, String destBucketName, String destFileName) {
        // No native support for moving blobs, so have to emulate with a two-step operation.
        Blob destBlob = copyBlob(sourceBlob, destBucketName, destFileName);
        deleteBlob(sourceBlob);
        return destBlob;
    }

    public void deleteBlob(Blob blob) {
        try {
            blob.delete();
        } catch (Exception e) {
            throw new DDPException("Failed to delete blob " + blob.getBlobId());
        }
    }
}

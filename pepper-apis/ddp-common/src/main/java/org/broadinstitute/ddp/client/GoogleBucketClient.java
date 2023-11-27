package org.broadinstitute.ddp.client;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.auth.Credentials;
import com.google.auth.ServiceAccountSigner;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.HttpMethod;
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

    public URL generateSignedUrl(ServiceAccountSigner signer,
                                 String bucketName, String blobName,
                                 long expirationTime, TimeUnit timeUnit,
                                 HttpMethod method, Map<String, String> headers) {
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, blobName)).build();
        return storage.signUrl(blobInfo, expirationTime, timeUnit,
                Storage.SignUrlOption.signWith(signer),
                Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.httpMethod(method),
                Storage.SignUrlOption.withExtHeaders(headers));
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

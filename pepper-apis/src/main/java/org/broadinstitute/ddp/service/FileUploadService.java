package org.broadinstitute.ddp.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
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
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleBucketUtil;
import org.broadinstitute.ddp.util.GuidUtils;
import org.jdbi.v3.core.Handle;

public class FileUploadService {

    public static final String DEFAULT_MIME_TYPE = "application/octet-stream";

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
        try {
            bucketCredentials = GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
            throw new DDPException("Could not get bucket credentials", e);
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

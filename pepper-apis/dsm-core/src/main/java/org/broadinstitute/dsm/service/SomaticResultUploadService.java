package org.broadinstitute.dsm.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import com.google.auth.ServiceAccountSigner;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.HttpMethod;
import com.typesafe.config.Config;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.client.GoogleBucketClient;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.dsm.db.SomaticResultUpload;
import org.broadinstitute.dsm.model.somatic.result.SomaticResultMetaData;
import org.broadinstitute.dsm.model.somatic.result.SomaticResultUploadSettings;
import org.owasp.fileio.FileValidator;

@Slf4j
public class SomaticResultUploadService {
    private final ServiceAccountSigner signer;
    private final GoogleBucketClient storageClient;
    private final Map<String, String> realmToUploadBucketMap;
    private final int maxSignedUrlMins;

    private final SomaticResultUploadSettings somaticUploadSettings;

    public static SomaticResultUploadService fromConfig(Config cfg) {
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
        Map<String, String> realmToUploadBucketMap = new HashMap<>();
        for (Config mappingCfg : cfg.getConfigList(ConfigFile.SomaticUploads.REALM_TO_BUCKET_MAPPINGS)) {
            String realm = ConfigUtil.getStrIfPresent(mappingCfg, "realm");
            String uploadBucket = ConfigUtil.getStrIfPresent(mappingCfg, ConfigFile.SomaticUploads.UPLOAD_BUCKET);
            realmToUploadBucketMap.putIfAbsent(realm, uploadBucket);
        }

        if (realmToUploadBucketMap.isEmpty()) {
            log.warn("No upload buckets configured for somatic file upload.  Please check configuration for {}",
                    ConfigFile.SomaticUploads.REALM_TO_BUCKET_MAPPINGS);
        }

        return new SomaticResultUploadService(
                signerCredentials,
                new GoogleBucketClient(projectId, bucketCredentials),
                realmToUploadBucketMap,
                cfg.getInt(ConfigFile.FileUploads.MAX_SIGNED_URL_MINS),
                new SomaticResultUploadSettings(cfg));
    }

    public SomaticResultUploadService(ServiceAccountSigner signer, GoogleBucketClient storageClient,
                                      Map<String, String> realmToUploadBucketMap, int maxSignedUrlMins,
                                      SomaticResultUploadSettings somaticResultUploadSettings) {
        this.signer = signer;
        this.storageClient = storageClient;
        this.realmToUploadBucketMap = realmToUploadBucketMap;
        this.maxSignedUrlMins = maxSignedUrlMins;
        this.somaticUploadSettings = somaticResultUploadSettings;
    }

    public AuthorizeResult authorizeUpload(String realm, String userId, String ddpParticipantId,
                                           SomaticResultMetaData somaticResultMetaData) {
        FileValidator fileValidator = new FileValidator();
        if (somaticResultMetaData.getFileSize() > somaticUploadSettings.getMaxFileSize()) {
            return new AuthorizeResult(AuthorizeResultType.FILE_SIZE_EXCEEDS_MAXIMUM, null,  somaticUploadSettings);
        }
        if (!somaticUploadSettings.getMimeTypes().contains(somaticResultMetaData.getMimeType())) {
            return new AuthorizeResult(AuthorizeResultType.MIME_TYPE_NOT_ALLOWED, null, somaticUploadSettings);
        }
        if (!fileValidator.isValidFileName("Validating file name",
                somaticResultMetaData.getFileName(), somaticUploadSettings.getAllowedFileExtensions(), false)) {
            return new AuthorizeResult(AuthorizeResultType.INVALID_FILE_NAME, null, somaticUploadSettings);
        }

        String fileUUID = UUID.randomUUID().toString();
        String blobPath = makeBlobPath(somaticResultMetaData, ddpParticipantId, realm, fileUUID);
        long userIdLong = Long.parseLong(userId);
        String uploadBucket = getUploadsBucket(realm);


        SomaticResultUpload.createFileUpload(realm, ddpParticipantId, somaticResultMetaData.getFileName(),
                somaticResultMetaData.getMimeType(), uploadBucket, blobPath, userIdLong);

        Map<String, String> headers = Map.of("Content-Type", somaticResultMetaData.getMimeType());

        URL signedURL = storageClient.generateSignedUrl(
                signer, uploadBucket, blobPath,
                maxSignedUrlMins, TimeUnit.MINUTES,
                HttpMethod.PUT, headers);

        return new AuthorizeResult(AuthorizeResultType.OK, signedURL, somaticUploadSettings);
    }

    public SomaticResultUpload deleteUpload(long userId, int documentId) {
        SomaticResultUpload deletedSomaticResultUpload = SomaticResultUpload.deleteDocumentByDocumentId(userId, documentId);
        Blob blobToDelete = storageClient.getBlob(deletedSomaticResultUpload.getBucket(), deletedSomaticResultUpload.getBlobPath());
        boolean deleted = blobToDelete.delete();
        if (deleted) {
            log.warn("User {} deleted somatic document {}", userId, documentId);
        } else {
            log.error("Somatic document failed to delete from GCS. Manual intervention required.  Last recorded bucket: {}, blobPath: {} ",
                    deletedSomaticResultUpload.getBucket(), deletedSomaticResultUpload.getBlobPath());
        }
        return deletedSomaticResultUpload;
    }

    public List<SomaticResultUpload> getSomaticResultsForParticipant(String realm, String ddpParticipantId) {
        return SomaticResultUpload.getSomaticFileUploadDocuments(realm, ddpParticipantId);
    }

    private static String makeBlobPath(SomaticResultMetaData payload, String userGuid, String studyGuid,
                                String fileGuid) {
        return String.format("%s/%s_%s_%s_%s",
                studyGuid, fileGuid, userGuid, getCurrentTimestamp(), payload.getFileName());
    }

    private static String getCurrentTimestamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").format(new Date());
    }

    private String getUploadsBucket(String realm) {
        if (this.realmToUploadBucketMap.isEmpty()) {
            throw new RuntimeException("Uploading documents not fully configured.  Please contact a DSM developer");
        }
        return this.realmToUploadBucketMap.get(realm.toLowerCase());
    }

    private enum AuthorizeResultType {
        FILE_SIZE_EXCEEDS_MAXIMUM,
        MIME_TYPE_NOT_ALLOWED,
        INVALID_FILE_NAME,
        OK
    }

    @Value
    @AllArgsConstructor
    public static class AuthorizeResult {
        AuthorizeResultType authorizeResultType;
        URL signedUrl;
        SomaticResultUploadSettings fileUploadSettings;
    }

}

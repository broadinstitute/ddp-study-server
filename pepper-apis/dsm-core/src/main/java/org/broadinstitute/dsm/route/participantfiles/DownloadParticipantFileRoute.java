package org.broadinstitute.dsm.route.participantfiles;

import static org.broadinstitute.dsm.statics.DBConstants.FILE_DOWNLOAD_ROLE;

import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.util.GoogleCredentialUtil;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.util.SecurityUtil;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;

@Slf4j
public class DownloadParticipantFileRoute extends RequestHandler {
    private static String googleProjectName;
    private static String BUCKET = "bucket";
    private static String FILE_NAME = "fileName";
    private static String QUARANTINE = "quarantine";
    private static String QUARANTINE_RESPONSE = "File is in the quarantine bucket and should not be downloaded!";


    public DownloadParticipantFileRoute(String googleProjectName) {
        this.googleProjectName = googleProjectName;
    }

    public static URL generateV4GetObjectSignedUrl(String projectId, String bucketName, String objectName) {
        // String projectId = "my-project-id";
        // String bucketName = "my-bucket";
        // String objectName = "my-object";
        GoogleCredentials bucketCredentials;
        boolean ensureDefault = true;
        bucketCredentials = GoogleCredentialUtil.initCredentials(ensureDefault);

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).setCredentials(bucketCredentials).build().getService();

        // Define resource
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, objectName)).build();

        URL url =
                storage.signUrl(blobInfo, 15, TimeUnit.MINUTES, Storage.SignUrlOption.withV4Signature());

        System.out.println("Generated GET signed URL:");
        System.out.println(url);
        System.out.println("You can use this URL with any user agent, for example:");
        System.out.println("curl '" + url + "'");
        return url;
    }

    public Optional<byte[]> downloadObject(String projectId, String bucketName, String objectName, String blobName) {
        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();
        Blob blob = storage.get(BlobId.of(bucketName, blobName));
        byte[] content = null;
        if (blob.exists()) {
            content = blob.getContent();
            log.info("Downloaded object " + objectName + " from bucket name " + bucketName);
        }
        return Optional.ofNullable(content);
    }

    @Override
    protected Object processRequest(Request request, spark.Response response, String userId) throws Exception {
        QueryParamsMap queryParams = request.queryMap();
        String realm = null;
        if (queryParams.value(RoutePath.REALM) != null) {
            realm = queryParams.get(RoutePath.REALM).value();
        }
        if (StringUtils.isBlank(realm)) {
            response.status(500);
            throw new RuntimeException("Realm was missing from the request");
        }
        String userIdR = UserUtil.getUserId(request);
        if (UserUtil.checkUserAccess(realm, userId, FILE_DOWNLOAD_ROLE, userIdR)) {
            String bucketName = queryParams.value(BUCKET);
            String fileName = queryParams.value(FILE_NAME);
            if (bucketName.indexOf(QUARANTINE) != -1) {
                log.warn(String.format("File %s is in the quarantine bucket %s and should not be downloaded!", fileName, bucketName));
                response.status(500);
                return QUARANTINE_RESPONSE;
            }
            URL url = generateV4GetObjectSignedUrl(googleProjectName, bucketName, fileName);
            response.status(200);
            return url;
        } else {
            response.status(401);
            return SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString();
        }
    }
}

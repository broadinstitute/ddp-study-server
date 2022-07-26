package org.broadinstitute.dsm.route.participantfiles;

import static org.broadinstitute.dsm.statics.DBConstants.FILE_DOWNLOAD_ROLE;

import java.util.Optional;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
    private static String BLOB = "blob";


    public DownloadParticipantFileRoute(String googleProjectName) {
        this.googleProjectName = googleProjectName;
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
            String objectName = queryParams.value(FILE_NAME);
            String blobName = queryParams.value(BLOB);
            Optional<byte[]> downloadObject =
                    downloadObject(googleProjectName, bucketName, objectName, blobName);
            byte[] downloadObjectArray = downloadObject.orElseThrow();
            response.status(200);
            return downloadObjectArray;
        } else {
            response.status(401);
            return SecurityUtil.ResultType.AUTHENTICATION_ERROR.toString();
        }
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
}

package org.broadinstitute.dsm.route.participantfiles;

import static org.broadinstitute.dsm.statics.DBConstants.FILE_DOWNLOAD_ROLE;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.dsm.security.RequestHandler;
import org.broadinstitute.dsm.statics.RoutePath;
import org.broadinstitute.dsm.statics.UserErrorMessages;
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;
import spark.Response;

@Slf4j
public class DownloadParticipantFileRoute extends RequestHandler {
    private static String googleProjectName;

    public DownloadParticipantFileRoute(String googleProjectName) {
        this.googleProjectName = googleProjectName;
    }

    @Override
    protected Object processRequest(Request request, Response response, String userId) throws Exception {
        log.info(request.url());
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
            String bucketName = queryParams.value("bucket");
            String objectName = queryParams.value("fileName");
            String blobName = queryParams.value("blob");
            String destPath = "~/Downloads/" + objectName;
            //todo check file is scanned and result is ok
            Optional<byte[]> downloadObject =
                    downloadObject(googleProjectName, bucketName, objectName, destPath, blobName, response.raw().getOutputStream());
            downloadObject.ifPresent(downloadObjectArray -> {
                try {
                    HttpServletResponse rawResponse = response.raw();
                    rawResponse.getOutputStream().write(downloadObjectArray);
                    rawResponse.setStatus(200);
                    rawResponse.getOutputStream().flush();
                    rawResponse.getOutputStream().close();
                } catch (IOException e) {
                    throw new RuntimeException("Couldn't make file ", e);
                }
            });
            downloadObject.orElseThrow();
            return null;

        }
        response.status(500);
        return UserErrorMessages.NO_RIGHTS;
    }

    public Optional<byte[]> downloadObject(String projectId, String bucketName, String objectName, String destFilePath,
                                           String blobName, OutputStream outputStream) {

        Storage storage = StorageOptions.newBuilder().setProjectId(projectId).build().getService();

        Blob blob = storage.get(BlobId.of(bucketName, blobName));
        byte[] content = null;
        if (blob.exists()) {

            content = blob.getContent();
            System.out.println(
                    "Downloaded object "
                            + objectName
                            + " from bucket name "
                            + bucketName
                            + " to "
                            + content);
        }
        return Optional.ofNullable(content);
    }
}

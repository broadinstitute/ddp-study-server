package org.broadinstitute.dsm.route.participantfiles;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
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
import org.broadinstitute.dsm.util.UserUtil;
import spark.QueryParamsMap;
import spark.Request;

@Slf4j
public class DownloadParticipantFileRoute extends RequestHandler {
    private static String googleProjectName;

    public DownloadParticipantFileRoute(String googleProjectName) {
        this.googleProjectName = googleProjectName;
    }

    @Override
    protected Response processRequest(Request request, spark.Response response, String userId) throws Exception {
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
        String bucketName = queryParams.value("bucket");
        String objectName = queryParams.value("fileName");
        String blobName = queryParams.value("blob");
        String destPath = "~/Downloads/" + objectName;
        //todo check file is scanned and result is ok
        Optional<byte[]> downloadObject =
                downloadObject(googleProjectName, bucketName, objectName, destPath, blobName, response.raw().getOutputStream());
        byte[] downloadObjectArray = downloadObject.orElseThrow();
//        File fileDownload =  new File(objectName);
//        FileUtils.writeByteArrayToFile(fileDownload, downloadObjectArray);
//        ResponseBuilder response2 = Response.ok(fileDownload);
//        response2.header("Content-Disposition", "attachment;filename=" + objectName);
        HttpServletResponse rawResponse = response.raw();
        rawResponse.getOutputStream().write(downloadObjectArray);
        rawResponse.setStatus(200);
        rawResponse.getOutputStream().flush();
        rawResponse.getOutputStream().close();
//        return response2.build();
        return null;
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

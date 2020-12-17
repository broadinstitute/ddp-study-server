package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import java.net.URL;

import com.google.cloud.storage.HttpMethod;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.FileUploadURLPayload;
import org.broadinstitute.ddp.json.FileUploadUrlResponse;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import spark.Request;
import spark.Response;

/**
 *  This route returns the URL for file upload
 */
public class CreateFileUploadUrlRoute extends ValidatedJsonInputRoute<FileUploadURLPayload> {

    private final FileUploadService fileUploadService;

    public CreateFileUploadUrlRoute(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public Object handle(Request request, Response response, FileUploadURLPayload payload) {
        String studyGuid = request.params(STUDY_GUID);
        String fileUploadGuid = GuidUtils.randomStandardGuid();
        HttpMethod httpMethod = payload.getResumable() ? HttpMethod.POST : HttpMethod.PUT;
        URL url = TransactionWrapper.withTxn(handle -> fileUploadService.getSignedURLForUpload(handle, fileUploadGuid, studyGuid,
                payload.getActivityInstanceGuid(),
                payload.getAnswerGuid(),
                payload.getFileName(),
                payload.getFileSize(),
                payload.getMimeType(),
                httpMethod));
        return new FileUploadUrlResponse(url.toString(), fileUploadGuid, payload.getResumable());
    }
}

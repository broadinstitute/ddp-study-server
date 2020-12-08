package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import java.net.URL;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.FileUploadURLPayload;
import org.broadinstitute.ddp.json.FileUploadUrlResponse;
import org.broadinstitute.ddp.service.FileUploadService;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 *  This route returns the URL for file upload
 */
public class GetFileUploadUrlRoute extends ValidatedJsonInputRoute<FileUploadURLPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(GetFileUploadUrlRoute.class);
    private final FileUploadService fileUploadService;

    public GetFileUploadUrlRoute(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public Object handle(Request request, Response response, FileUploadURLPayload payload) {
        String studyGuid = request.params(STUDY_GUID);
        String fileUploadGuid = GuidUtils.randomStandardGuid();
        URL url = TransactionWrapper.withTxn(handle -> fileUploadService.getSignedURLForUpload(handle, fileUploadGuid, studyGuid,
                payload.getActivityCode(),
                payload.getActivityInstanceGuid(),
                payload.getAnswerGuid(),
                payload.getFileName(),
                payload.getFileSize(),
                payload.getCreatedDate(),
                payload.getMimeType()));
        return new FileUploadUrlResponse(url.toString(), fileUploadGuid);
    }
}

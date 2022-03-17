package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.service.FileUploadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetUserFileAnswersRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetUserFileAnswersRoute.class);

    private final FileUploadService fileUploadService;

    public GetUserFileAnswersRoute(FileUploadService fileUploadService) {
        this.fileUploadService = fileUploadService;
    }

    @Override
    public Object handle(Request request, Response response) {
        String participantGuid = request.params(USER_GUID);
        String studyGuid = request.params(STUDY_GUID);
        LOG.info("Retrieving uploaded files for participant: {})", participantGuid);

        return TransactionWrapper.withTxn(handle -> fileUploadService
                .getUserUploads(handle, participantGuid, studyGuid));
    }
}

package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.study.StudyParticipantsInfo;
import org.broadinstitute.ddp.service.OLCService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetParticipantInfoRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetParticipantInfoRoute.class);

    public GetParticipantInfoRoute() {}

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(STUDY_GUID);
        LOG.info("Requesting enrolled participant info with GUID: {}", studyGuid);

        StudyParticipantsInfo participantsInfo =
                TransactionWrapper.withTxn(handle -> OLCService.getAllOLCsForEnrolledParticipantsInStudy(handle, studyGuid));

        if (participantsInfo == null) {
            String errorMsg = "Study was not configured correctly to return enrolled participant info";
            ResponseUtil.haltError(response,
                    HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    new ApiError(ErrorCodes.UNSATISFIED_PRECONDITION, errorMsg));
            return null;
        } else {
            return participantsInfo;
        }
    }
}

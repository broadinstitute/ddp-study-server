package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.PREVIOUS_LAST_KIT_REQUEST_ID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

public class GetDsmKitRequestsRoute implements Route {
    private static final Logger LOG = LoggerFactory.getLogger(GetDsmKitRequestsRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        return TransactionWrapper.withTxn(handle -> {
            String studyGuid = request.params(STUDY_GUID);
            if (studyGuid == null) {
                LOG.error("Study GUID not found in request");
                ResponseUtil.haltError(response, 422,
                        new ApiError(ErrorCodes.MISSING_STUDY_GUID, "Study GUID is missing"));
                return null;
            }
            String lastKitRequestGuid = request.params(PREVIOUS_LAST_KIT_REQUEST_ID);
            DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
            if (lastKitRequestGuid != null) {
                return kitDao.findKitRequestsAfterGuidWithStatus(studyGuid, lastKitRequestGuid,
                        DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS,
                        DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS);
            } else {
                return kitDao.findAllKitRequestsForStudyWithStatus(studyGuid,
                        DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS,
                        DsmAddressValidationStatus.DSM_EASYPOST_SUGGESTED_ADDRESS_STATUS);
            }
        });


    }
}

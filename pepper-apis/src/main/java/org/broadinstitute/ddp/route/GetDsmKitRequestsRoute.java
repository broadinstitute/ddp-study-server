package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.PREVIOUS_LAST_KIT_REQUEST_ID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.STUDY_GUID;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudyCached;
import org.broadinstitute.ddp.db.dto.StudyDto;
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
            StudyDto studyDto = new JdbiUmbrellaStudyCached(handle).findByStudyGuid(studyGuid);
            if (studyDto == null) {
                var err = new ApiError(ErrorCodes.STUDY_NOT_FOUND, "Could not find study with guid " + studyGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
            }

            String lastKitRequestGuid = request.params(PREVIOUS_LAST_KIT_REQUEST_ID);
            DsmKitRequestDao kitDao = handle.attach(DsmKitRequestDao.class);
            if (lastKitRequestGuid != null) {
                if (kitDao.findKitRequest(lastKitRequestGuid).isEmpty()) {
                    // This might indicate that DSM is out-of-sync with Pepper, so log an error.
                    LOG.error("DSM asked for kits since last kit request guid {} for study {}"
                            + " but kit guid is not found", lastKitRequestGuid, studyGuid);
                    var err = new ApiError(ErrorCodes.NOT_FOUND, "Could not find kit request with guid " + lastKitRequestGuid);
                    throw ResponseUtil.haltError(HttpStatus.SC_NOT_FOUND, err);
                }
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

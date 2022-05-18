package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.ADDRESS_GUID;
import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import spark.Request;
import spark.Response;

@Slf4j
public class UpdateMailAddressRoute extends ValidatedMailAddressInputRoute {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    public UpdateMailAddressRoute(AddressService addressService) {
        super(addressService);
    }

    @Override
    public Object handleInputRequest(Request request, Response response, MailAddress updatedMailAddress) {
        String participantGuid = request.params(USER_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        String addressGuid = request.params(ADDRESS_GUID);
        log.info("Updating mailing address for participant {} by operator {} with guid: {}",
                participantGuid, operatorGuid, addressGuid);
        if (log.isDebugEnabled()) {
            log.debug("About to update: {}", GSON.toJson(updatedMailAddress));
        }

        return TransactionWrapper.withTxn(handle -> {
            boolean wasUpdated = addressService.updateAddress(handle, addressGuid, updatedMailAddress,
                    participantGuid, operatorGuid);

            if (wasUpdated) {
                EventDao eventDao = handle.attach(EventDao.class);
                handle.attach(JdbiUserStudyEnrollment.class)
                        .getAllLatestEnrollmentsForUser(participantGuid).stream()
                        .filter(enrollmentStatusDto -> enrollmentStatusDto.getEnrollmentStatus().isEnrolled())
                        .forEach(enrollmentStatusDto -> {
                            int numQueued = eventDao.addMedicalUpdateTriggeredEventsToQueue(
                                    enrollmentStatusDto.getStudyId(), enrollmentStatusDto.getUserId());
                            log.info("Queued {} medical-update events for participantGuid={} studyGuid={}",
                                    numQueued, enrollmentStatusDto.getUserGuid(), enrollmentStatusDto.getStudyGuid());
                        });
                handle.attach(DataExportDao.class).queueDataSync(participantGuid);
                response.status(HttpStatus.SC_NO_CONTENT);
                return "";
            } else {
                String errorMsg = "Mail address with guid '" + addressGuid + "' was not found";
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.MAIL_ADDRESS_NOT_FOUND, errorMsg));
            }
        });
    }
}

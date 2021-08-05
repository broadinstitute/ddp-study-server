package org.broadinstitute.ddp.route;

import static org.broadinstitute.ddp.constants.RouteConstants.PathParam.USER_GUID;

import java.util.concurrent.locks.Lock;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.RouteConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.util.RouteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

public class CreateMailAddressRoute extends ValidatedMailAddressInputRoute {
    private static final Logger LOG = LoggerFactory.getLogger(CreateMailAddressRoute.class);

    public CreateMailAddressRoute(AddressService addressService) {
        super(addressService);
    }

    @Override
    public Object handleInputRequest(Request request, Response response, MailAddress dataObject) {
        String participantGuid = request.params(USER_GUID);
        String operatorGuid = RouteUtil.getDDPAuth(request).getOperator();
        LOG.info("Creating mailing address for participant {} by operator {}", participantGuid, operatorGuid);
        if (LOG.isDebugEnabled()) {
            LOG.debug("About to save: {}", getGson().toJson(dataObject));
        }
        MailAddress addedAddress;
        Lock lock = null;
        // lock the block where default address inserted
        // to be sure that address snapshotting will be done after the default address created
        // (snapshotting done in AddressService.snapshotAddress())
        if (addressService.isUseMailAddressLocks()) {
            lock = addressService.getAddMailAddressLocks().addAndLock(participantGuid);
        }
        try {
            addedAddress = TransactionWrapper.withTxn(handle -> {
                MailAddress addr = addressService.addAddress(handle, dataObject, participantGuid, operatorGuid);
                EventDao eventDao = handle.attach(EventDao.class);
                handle.attach(JdbiUserStudyEnrollment.class)
                        .getAllLatestEnrollmentsForUser(participantGuid).stream()
                        .filter(enrollmentStatusDto -> enrollmentStatusDto.getEnrollmentStatus().isEnrolled())
                        .forEach(enrollmentStatusDto -> {
                            int numQueued = eventDao.addMedicalUpdateTriggeredEventsToQueue(
                                    enrollmentStatusDto.getStudyId(), enrollmentStatusDto.getUserId());
                            LOG.info("Queued {} medical-update events for participantGuid={} studyGuid={}",
                                    numQueued, enrollmentStatusDto.getUserGuid(), enrollmentStatusDto.getStudyGuid());
                        });
                handle.attach(DataExportDao.class).queueDataSync(participantGuid);
                return addr;
            });
        } finally {
            if (addressService.isUseMailAddressLocks() && lock != null) {
                lock.unlock();
            }
        }
        response.status(HttpStatus.SC_CREATED);
        String locationHeaderValue = buildMailAddressUrl(request, participantGuid, addedAddress);
        response.header(HttpHeaders.LOCATION, locationHeaderValue);
        LOG.info("Successfully created mailing address for participant {} by operator {}", participantGuid, operatorGuid);
        return addedAddress;
    }

    private String buildMailAddressUrl(Request request, String participantGuid, MailAddress addedAddress) {
        return request.scheme() + "://" + request.host()
                    + (RouteConstants.API.ADDRESS.replace(RouteConstants.PathParam.ADDRESS_GUID, addedAddress.getGuid())
                    .replace(RouteConstants.PathParam.USER_GUID, participantGuid));
    }
}

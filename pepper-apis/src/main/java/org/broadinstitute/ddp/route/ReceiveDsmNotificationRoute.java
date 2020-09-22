package org.broadinstitute.ddp.route;

import java.time.Instant;
import java.util.List;
import javax.validation.ValidationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.dsm.DsmKitRequest;
import org.broadinstitute.ddp.json.dsm.DsmNotificationPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
import org.broadinstitute.ddp.model.dsm.KitTypes;
import org.broadinstitute.ddp.model.dsm.TestResult;
import org.broadinstitute.ddp.model.event.DsmNotificationSignal;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.service.EventService;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.broadinstitute.ddp.util.RouteUtil;
import org.broadinstitute.ddp.util.ValidatedJsonInputRoute;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;

/**
 * A hook called by DSM at a certain stage, triggers the notification sending when invoked
 * Given a user GUID, a study GUID and a notification event type, looks for a notification template
 * and queues an event which is picked up by Housekeeping later and results in sending the notification
 * Returns:
 *    200 OK - the notification event was queued or no event configuration exists
 *    404 Not Found - no study, user or notification event type found
 */
public class ReceiveDsmNotificationRoute extends ValidatedJsonInputRoute<DsmNotificationPayload> {

    private static final Logger LOG = LoggerFactory.getLogger(ReceiveDsmNotificationRoute.class);

    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, DsmNotificationPayload payload) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuidOrAltPid = request.params(PathParam.USER_GUID);

        LOG.info("Received DSM notification event for userGuidOrAltPid={}, studyGuid={}, and eventType={}",
                participantGuidOrAltPid, studyGuid, payload.getEventType());

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findPotentiallyLegacyUserAndStudyOrHalt(handle, participantGuidOrAltPid, studyGuid);
            StudyDto studyDto = found.getStudyDto();
            User user = found.getUser();
            String userGuid = user.getGuid();

            EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user.getId(), studyDto.getId())
                    .orElse(null);
            if (status != EnrollmentStatusType.ENROLLED) {
                var err = new ApiError(ErrorCodes.UNSATISFIED_PRECONDITION,
                        "User " + userGuid + " is not enrolled in study " + studyDto.getGuid());
                LOG.error(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            }

            DsmNotificationEventType eventType = payload.parseEventTypeCode().orElseThrow(() -> {
                var err = new ApiError(ErrorCodes.NOT_FOUND,
                        "Notification event type '" + payload.getEventType() + "' is not recognized");
                LOG.warn(err.getMessage());
                return ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            });

            boolean isNewEvent = processEvent(handle, response, eventType, payload);
            if (!isNewEvent) {
                LOG.warn("Notification {} event is not a new event, skipping", eventType);
                response.status(HttpStatus.SC_OK);
                return null;
            }

            TestResult testResult = null;
            if (eventType == DsmNotificationEventType.TEST_RESULT) {
                testResult = parseTestResult(response, payload);
            }

            LOG.info("Running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            var signal = new DsmNotificationSignal(
                    user.getId(),
                    user.getId(),
                    userGuid,
                    studyDto.getId(),
                    eventType,
                    testResult);
            EventService.getInstance().processAllActionsForEventSignal(handle, signal);

            LOG.info("Finished running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            response.status(HttpStatus.SC_OK);
            return null;
        });
    }

    private boolean processEvent(Handle handle, Response response, DsmNotificationEventType eventType, DsmNotificationPayload payload) {
        boolean isNewEvent = true;
        String kitRequestGuid = payload.getKitRequestGuid();
        if (StringUtils.isNotBlank(kitRequestGuid)) {
            DsmKitRequestDao kitRequestDao = handle.attach(DsmKitRequestDao.class);
            DsmKitRequest kit = kitRequestDao.findKitRequestByGuid(kitRequestGuid).orElse(null);
            if (kit == null) {
                var err = new ApiError(ErrorCodes.NOT_FOUND, "Could not find kit with id " + kitRequestGuid);
                LOG.warn(err.getMessage());
                throw ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            }

            if (KitTypes.TESTBOSTON.name().equalsIgnoreCase(kit.getKitType())) {
                Instant eventTime = payload.getEventTime();
                if (eventTime == null) {
                    var err = new ApiError(ErrorCodes.BAD_PAYLOAD, "Missing event timestamp");
                    LOG.warn(err.getMessage());
                    throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
                }

                switch (eventType) {
                    case TESTBOSTON_SENT:
                        if (kit.getDeliveredAt() != null || kit.getReceivedBackAt() != null) {
                            LOG.error("Received {} event but kit {} has delivered_at={}, received_back_at={}",
                                    eventType, kitRequestGuid, kit.getDeliveredAt(), kit.getReceivedBackAt());
                            isNewEvent = false;
                        } else if (kit.getShippedAt() != null) {
                            LOG.error("Received {} event but kit {} already has shipped_at={}",
                                    eventType, kitRequestGuid, kit.getShippedAt());
                            isNewEvent = false;
                        } else {
                            kitRequestDao.updateShippedAt(kit.getId(), eventTime);
                            LOG.info("Updated kit {} with shipped_at={}", kitRequestGuid, eventTime);
                            isNewEvent = true;
                        }
                        break;
                    case TESTBOSTON_DELIVERED:
                        if (kit.getReceivedBackAt() != null) {
                            LOG.error("Received {} event but kit {} has received_back_at={}",
                                    eventType, kitRequestGuid, kit.getReceivedBackAt());
                            isNewEvent = false;
                        } else if (kit.getDeliveredAt() != null) {
                            LOG.error("Received {} event but kit {} already has delivered_at={}",
                                    eventType, kitRequestGuid, kit.getDeliveredAt());
                            isNewEvent = false;
                        } else if (kit.getShippedAt() == null) {
                            LOG.error("Received {} event but kit {} has not been shipped yet", eventType, kitRequestGuid);
                            isNewEvent = false;
                        } else {
                            kitRequestDao.updateDeliveredAt(kit.getId(), eventTime);
                            LOG.info("Updated kit {} with delivered_at={}", kitRequestGuid, eventTime);
                            isNewEvent = true;
                        }
                        break;
                    case TESTBOSTON_RECEIVED:
                        if (kit.getReceivedBackAt() != null) {
                            LOG.error("Received {} event but kit {} already has received_back_at={}",
                                    eventType, kitRequestGuid, kit.getReceivedBackAt());
                            isNewEvent = false;
                        } else if (kit.getDeliveredAt() == null || kit.getShippedAt() == null) {
                            LOG.error("Received {} event but kit {} has not been shipped or delivered yet", eventType, kitRequestGuid);
                            isNewEvent = false;
                        } else {
                            kitRequestDao.updateReceivedBackAt(kit.getId(), eventTime);
                            LOG.info("Updated kit {} with received_back_at={}", kitRequestGuid, eventTime);
                            isNewEvent = true;
                        }
                        break;
                    default:
                        LOG.error("Received unexpected {} event for kit {} of type {}", eventType, kitRequestGuid, kit.getKitType());
                        isNewEvent = false;
                        break;
                }
            }
        }
        return isNewEvent;
    }

    private TestResult parseTestResult(Response response, DsmNotificationPayload payload) {
        TestResult testResult;
        try {
            testResult = getGson().fromJson(payload.getEventData(), TestResult.class);
        } catch (Exception e) {
            LOG.error("Error while parsing test result event data", e);
            var err = new ApiError(ErrorCodes.BAD_PAYLOAD, "Error while parsing event data");
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
        }

        if (testResult == null) {
            var err = new ApiError(ErrorCodes.BAD_PAYLOAD, "Missing test result event data");
            throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
        }

        try {
            List<JsonValidationError> validationErrors = getValidator().validateAsJson(testResult);
            if (!validationErrors.isEmpty()) {
                var err = new ApiError(ErrorCodes.BAD_PAYLOAD, buildErrorMessage(validationErrors));
                throw ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, err);
            }
        } catch (ValidationException e) {
            LOG.error("Error while validating test result event data", e);
            var err = new ApiError(ErrorCodes.SERVER_ERROR, "Error while validating event data");
            throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
        }

        return testResult;
    }
}

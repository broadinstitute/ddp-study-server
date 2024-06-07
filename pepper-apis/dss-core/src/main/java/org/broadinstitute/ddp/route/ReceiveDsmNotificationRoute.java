package org.broadinstitute.ddp.route;

import javax.validation.ValidationException;
import java.time.Instant;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DataExportDao;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.notficationevent.DsmNotificationPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.notficationevent.DsmNotificationEventType;
import org.broadinstitute.ddp.notficationevent.KitReasonType;
import org.broadinstitute.ddp.model.dsm.KitType;
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
@Slf4j
public class ReceiveDsmNotificationRoute extends ValidatedJsonInputRoute<DsmNotificationPayload> {
    @Override
    protected int getValidationErrorStatus() {
        return HttpStatus.SC_BAD_REQUEST;
    }

    @Override
    public Object handle(Request request, Response response, DsmNotificationPayload payload) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuidOrAltPid = request.params(PathParam.USER_GUID);

        log.info("Received DSM notification event for userGuidOrAltPid={}, studyGuid={}, eventType={}, kitRequestId={}, kitReasonType={}",
                participantGuidOrAltPid, studyGuid, payload.getEventType(), payload.getKitRequestId(), payload.getKitReasonType());

        return TransactionWrapper.withTxn(handle -> {
            var found = RouteUtil.findPotentiallyLegacyUserAndStudyOrHalt(handle, participantGuidOrAltPid, studyGuid);
            StudyDto studyDto = found.getStudyDto();
            User user = found.getUser();
            String userGuid = user.getGuid();
            User proxy = found.getProxy();

            EnrollmentStatusType status = handle.attach(JdbiUserStudyEnrollment.class)
                    .getEnrollmentStatusByUserAndStudyIds(user.getId(), studyDto.getId())
                    .orElse(null);
            if (status == null || !status.isEnrolled()) {
                String msg = String.format(
                        "User %s with status %s is not enrolled in study %s, will not process DSM notification event %s",
                        userGuid, status == null ? "<null>" : status, studyGuid, payload.getEventType());
                if (status == EnrollmentStatusType.EXITED_AFTER_ENROLLMENT) {
                    // Receiving kit notifications for withdrawn participants can occur during normal operations, let's log as a warning.
                    log.warn(msg);
                } else {
                    // Status is unusual, for example receiving a kit when participant status is still only registered, let's report it.
                    log.error(msg);
                }
                var err = new ApiError(ErrorCodes.UNSATISFIED_PRECONDITION,
                        "User " + userGuid + " is not enrolled in study " + studyGuid);
                throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
            }

            DsmNotificationEventType eventType = payload.parseEventTypeCode().orElseThrow(() -> {
                var err = new ApiError(ErrorCodes.NOT_FOUND,
                        "Notification event type '" + payload.getEventType() + "' is not recognized");
                log.warn(err.getMessage());
                return ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
            });

            TestResult testResult = null;
            if (eventType == DsmNotificationEventType.TEST_RESULT) {
                testResult = parseTestResult(response, payload);
            }

            if (eventType == DsmNotificationEventType.TESTBOSTON_SENT) {
                KitType kitType = handle.attach(KitTypeDao.class).getTestBostonKitType();
                recordInitialKitSentTime(handle, studyDto, user, kitType, Instant.now());
            }

            KitReasonType kitReasonType = payload.getKitReasonType() != null
                    ? payload.getKitReasonType() : KitReasonType.NORMAL;

            log.info("Running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            var signal = new DsmNotificationSignal(
                    proxy != null ? proxy.getId() : user.getId(),
                    user.getId(),
                    userGuid,
                    proxy != null ? proxy.getGuid() : userGuid,
                    studyDto.getId(),
                    studyDto.getGuid(),
                    eventType,
                    payload.getKitRequestId(),
                    kitReasonType,
                    testResult);
            EventService.getInstance().processAllActionsForEventSignal(handle, signal);

            // User data likely changed after executing events, let's request a data sync.
            handle.attach(DataExportDao.class).queueDataSync(user.getId(), studyDto.getId());

            log.info("Finished running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            response.status(HttpStatus.SC_OK);
            return null;
        });
    }

    private TestResult parseTestResult(Response response, DsmNotificationPayload payload) {
        TestResult testResult;
        try {
            testResult = getGson().fromJson(payload.getEventData(), TestResult.class);
        } catch (Exception e) {
            log.error("Error while parsing test result event data", e);
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
            log.error("Error while validating test result event data", e);
            var err = new ApiError(ErrorCodes.SERVER_ERROR, "Error while validating event data");
            throw ResponseUtil.haltError(response, HttpStatus.SC_INTERNAL_SERVER_ERROR, err);
        }

        return testResult;
    }

    private void recordInitialKitSentTime(Handle handle, StudyDto studyDto, User user, KitType kitType, Instant timestamp) {
        long kitConfigId = handle.attach(KitConfigurationDao.class)
                .getKitConfigurationDtosByStudyId(studyDto.getId())
                .stream()
                .filter(kit -> kit.getKitTypeId() == kitType.getId())
                .map(KitConfigurationDto::getId)
                .findFirst()
                .orElseThrow(() -> new DDPException(String.format(
                        "Could not find kit configuration for study %s and kit type %s",
                        studyDto.getGuid(), kitType.getName())));

        var kitScheduleDao = handle.attach(KitScheduleDao.class);
        var record = kitScheduleDao.findRecord(user.getId(), kitConfigId).orElse(null);

        if (record != null && record.getInitialKitSentTime() == null) {
            kitScheduleDao.updateRecordInitialKitSentTime(record.getId(), timestamp);
            log.info("Updated initial kit sent time to {} for participant={}, study={}, kitConfigurationId={}",
                    timestamp, user.getGuid(), studyDto.getGuid(), kitConfigId);
        }
    }
}

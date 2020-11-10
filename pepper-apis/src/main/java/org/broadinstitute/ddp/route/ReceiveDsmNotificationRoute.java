package org.broadinstitute.ddp.route;

import javax.validation.ValidationException;
import java.time.Instant;
import java.util.List;

import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.dsm.DsmNotificationPayload;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.model.activity.types.DsmNotificationEventType;
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

            TestResult testResult = null;
            if (eventType == DsmNotificationEventType.TEST_RESULT) {
                testResult = parseTestResult(response, payload);
            }

            if (eventType == DsmNotificationEventType.TESTBOSTON_SENT) {
                KitType kitType = handle.attach(KitTypeDao.class).getTestBostonKitType();
                recordInitialKitSentTime(handle, studyDto, user, kitType, Instant.now());
            }

            LOG.info("Running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            var signal = new DsmNotificationSignal(
                    user.getId(),
                    user.getId(),
                    userGuid,
                    null,
                    studyDto.getId(),
                    eventType,
                    testResult);
            EventService.getInstance().processAllActionsForEventSignal(handle, signal);

            LOG.info("Finished running events for userGuid={} and DSM notification eventType={}", userGuid, eventType);
            response.status(HttpStatus.SC_OK);
            return null;
        });
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
            LOG.info("Updated initial kit sent time to {} for participant={}, study={}, kitConfigurationId={}",
                    timestamp, user.getGuid(), studyDto.getGuid(), kitConfigId);
        }
    }
}

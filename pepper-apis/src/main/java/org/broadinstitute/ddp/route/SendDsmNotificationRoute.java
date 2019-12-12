package org.broadinstitute.ddp.route;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.http.HttpStatus;
import org.broadinstitute.ddp.constants.ErrorCodes;
import org.broadinstitute.ddp.constants.RouteConstants.PathParam;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiDsmNotificationEventType;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.json.dsm.DsmNotificationEvent;
import org.broadinstitute.ddp.json.errors.ApiError;
import org.broadinstitute.ddp.util.RequestUtil;
import org.broadinstitute.ddp.util.ResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spark.Request;
import spark.Response;
import spark.Route;

/**
 * A hook called by DSM at a certain stage, triggers the notification sending when invoked
 * Given a user GUID, a study GUID and a notification event type, looks for a notification template
 * and queues an event which is picked up by Houskeeping later and results in sending the notification
 * Returns:
 *    200 OK - the notification event was queued or no event configuration exists
 *    404 Not Found - no study, user or notification event type found
 */
public class SendDsmNotificationRoute implements Route {

    private static final Logger LOG = LoggerFactory.getLogger(SendDsmNotificationRoute.class);

    @Override
    public Object handle(Request request, Response response) {
        String studyGuid = request.params(PathParam.STUDY_GUID);
        String participantGuidOrLegacyAltPid = request.params(PathParam.USER_GUID);
        String requestBody = request.body();
        DsmNotificationEvent dsmEvent = null;
        try {
            dsmEvent = new Gson().fromJson(requestBody, DsmNotificationEvent.class);
        } catch (JsonSyntaxException e) {
            String errMsg = "Bad payload: failed to deserialize the following JSON: " + requestBody;
            LOG.warn(errMsg);
            ResponseUtil.haltError(response, HttpStatus.SC_BAD_REQUEST, new ApiError(ErrorCodes.BAD_PAYLOAD, errMsg));
        }
        String notificationEventType = RequestUtil.urlComponentToStringConstant(dsmEvent.getEventType());
        LOG.info("Trying to queue an event for the user {}, study {}, and event type {}",
                participantGuidOrLegacyAltPid, studyGuid, notificationEventType);
        return TransactionWrapper.withTxn(
                handle -> {
                    StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
                    // No study found, respond with HTTP 404
                    if (studyDto == null) {
                        String errMsg = "A study with GUID " + studyGuid + " you try to notify is not found";
                        LOG.warn(errMsg);
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                        return null;
                    }
                    UserDto userDto = handle.attach(JdbiUser.class).findByLegacyAltPidIfNotFoundByUserGuid(participantGuidOrLegacyAltPid);
                    if (userDto == null) {
                        ApiError err = new ApiError(ErrorCodes.USER_NOT_FOUND, "Could not find participant "
                                + "with GUID or Legacy AltPid " + participantGuidOrLegacyAltPid);
                        LOG.warn(err.getMessage());
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, err);
                        return null;
                    }

                    String userGuid = userDto.getUserGuid();

                    // No DSM notification event type found, respond with HTTP 404
                    Optional<Long> evtTypeId = handle.attach(JdbiDsmNotificationEventType.class).findIdByCode(notificationEventType);
                    if (!evtTypeId.isPresent()) {
                        String errMsg = "A notification event with code " + notificationEventType + " you try to trigger is not found";
                        LOG.warn(errMsg);
                        ResponseUtil.haltError(response, HttpStatus.SC_NOT_FOUND, new ApiError(ErrorCodes.NOT_FOUND, errMsg));
                        return null;
                    }
                    LOG.info("Trying to fetch event configurations for the study {} and event type {}", studyGuid, notificationEventType);
                    List<Long> eventConfigurationIds = handle.attach(EventDao.class)
                            .getDsmNotificationConfigurationIds(
                                    studyGuid,
                                    userGuid,
                                    notificationEventType
                            );
                    // No event configurations found, no notification will be sent
                    if (eventConfigurationIds.isEmpty()) {
                        String errMsg = "No event configuration for the study with GUID " + studyGuid
                                + " and notification event type " + notificationEventType + " exists";
                        LOG.warn(errMsg);
                        return null;
                    }
                    // insert the record to the "queued_event" and subclass tables
                    QueuedEventDao queuedEventDao = handle.attach(QueuedEventDao.class);

                    eventConfigurationIds.forEach(eid -> queuedEventDao.insertNotification(eid, 0L,
                            userDto.getUserId(),
                            null,
                            Map.of()));
                    // All fine, the notification(s) were queued. Respond with HTTP 200
                    LOG.info("Successfully queued {} event configurations for the user {}", eventConfigurationIds.size(), userGuid);
                    response.status(HttpStatus.SC_OK);
                    return null;
                }
        );
    }

}

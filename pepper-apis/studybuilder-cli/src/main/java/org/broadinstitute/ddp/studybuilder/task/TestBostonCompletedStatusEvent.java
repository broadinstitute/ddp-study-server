package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.UserStatusChangedTrigger;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * One-off task to add event for user enrollment status changes and doing necessary data backfill.
 */
@Slf4j
public class TestBostonCompletedStatusEvent implements CustomTask {
    private static final String STUDY_GUID = "testboston";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        Long statusEnrolledEventId = null;
        Integer delaySeconds = null;
        boolean doBackfill = false;

        var statusEnrolled = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGED)
                .map(event -> (UserStatusChangedTrigger) event.getEventTrigger())
                .filter(trigger -> trigger.getTargetStatusType() == EnrollmentStatusType.ENROLLED)
                .findFirst().orElse(null);
        if (statusEnrolled == null) {
            Config eventCfg = studyCfg.getConfigList("events").stream()
                    .filter(event -> "USER_STATUS_CHANGED".equals(event.getString("trigger.type")))
                    .filter(event -> "ENROLLED".equals(event.getString("trigger.status")))
                    .findFirst().orElseThrow(() ->
                            new DDPException("Could not find (USER_STATUS_CHANGED, ENROLLED) event in study config"));
            statusEnrolledEventId = eventBuilder.insertEvent(handle, eventCfg);
            delaySeconds = eventCfg.getInt("delaySeconds");
            doBackfill = true;
        } else {
            log.info("Already has (USER_STATUS_CHANGED, ENROLLED) event configuration with id {}",
                    statusEnrolled.getEventConfigurationDto().getEventConfigurationId());
        }

        var statusCompleted = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGED)
                .map(event -> (UserStatusChangedTrigger) event.getEventTrigger())
                .filter(trigger -> trigger.getTargetStatusType() == EnrollmentStatusType.COMPLETED)
                .findFirst().orElse(null);
        if (statusCompleted == null) {
            Config eventCfg = studyCfg.getConfigList("events").stream()
                    .filter(event -> "USER_STATUS_CHANGED".equals(event.getString("trigger.type")))
                    .filter(event -> "COMPLETED".equals(event.getString("trigger.status")))
                    .findFirst().orElseThrow(() ->
                            new DDPException("Could not find (USER_STATUS_CHANGED, COMPLETED) event in study config"));
            eventBuilder.insertEvent(handle, eventCfg);
        } else {
            log.info("Already has (USER_STATUS_CHANGED, COMPLETED) event configuration with id {}",
                    statusCompleted.getEventConfigurationDto().getEventConfigurationId());
        }

        if (doBackfill) {
            runBackfill(handle, studyDto, statusEnrolledEventId, delaySeconds);
        }
    }

    private void runBackfill(Handle handle, StudyDto studyDto, long statusEnrolledEventId, int delaySeconds) {
        var helper = handle.attach(SqlHelper.class);
        long delayMillis = delaySeconds * 1000L;
        long nowMillis = Instant.now().toEpochMilli();

        log.info("Starting backfill with eventConfigId={}, delayMillis={}, nowMillis={}",
                statusEnrolledEventId, delayMillis, nowMillis);

        List<Long> userIds = helper.findEligibleCompletedUsers(studyDto.getId(), delayMillis, nowMillis);
        log.info("Found {} participants that should be marked as COMPLETED", userIds.size());

        // Idea here is to leverage existing event mechanism to update their status to COMPLETED.
        // We're using TestBoston's "status changed to ENROLLED" event but with zero delay. This
        // will effectively trigger the same set of events for the participant as if these events
        // had existed in the first place. The expected result is that Housekeeping will pick up
        // on these queued events, then change their status to COMPLETED, and lastly send them the
        // "you're done" email.
        var queueDao = handle.attach(QueuedEventDao.class);
        for (var userId : userIds) {
            long queuedId = queueDao.addToQueue(statusEnrolledEventId, userId, userId, 0);
            log.info("Queued status change event for participant {} with queued_event_id={}", userId, queuedId);
        }

        log.info("Finished backfill");
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select usen.user_id"
                + "  from user_study_enrollment as usen"
                + "  join enrollment_status_type as est on est.enrollment_status_type_id = usen.enrollment_status_type_id"
                + "  join (select usen1.user_id, min(usen1.valid_from) as first_enrolled_at"
                + "          from user_study_enrollment as usen1"
                + "          join enrollment_status_type as est1 on est1.enrollment_status_type_id = usen1.enrollment_status_type_id"
                + "         where usen1.study_id = :studyId"
                + "           and est1.enrollment_status_type_code = 'ENROLLED'"
                + "         group by usen1.user_id"
                + "       ) as first_enrolled on first_enrolled.user_id = usen.user_id"
                + " where usen.study_id = :studyId"
                + "   and est.enrollment_status_type_code = 'ENROLLED'"
                + "   and usen.valid_to is null"
                + "   and (first_enrolled.first_enrolled_at + :delayMillis) <= :nowMillis")
        List<Long> findEligibleCompletedUsers(
                @Bind("studyId") long studyId,
                @Bind("delayMillis") long delayMillis,
                @Bind("nowMillis") long nowMillis);
    }
}

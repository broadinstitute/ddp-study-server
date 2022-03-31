package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.QueuedEventDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

/**
 * One-off task to find participants that should be `completed` and also queue up events for ones that's missing one.
 */
@Slf4j
public class TestBostonBackfillCompleted implements CustomTask {
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

        var statusChangeEvent = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGED)
                .filter(event -> event.getEventActionType() == EventActionType.UPDATE_USER_STATUS)
                .findFirst().orElse(null);
        if (statusChangeEvent == null) {
            throw new DDPException("Could not find USER_STATUS_CHANGED event configuration");
        }
        if (statusChangeEvent.getPostDelaySeconds() == null || statusChangeEvent.getPostDelaySeconds() <= 0) {
            throw new DDPException("Event does not have delaySeconds setting");
        }

        runBackfill(handle, studyDto, statusChangeEvent);
    }

    private void runBackfill(Handle handle, StudyDto studyDto, EventConfiguration event) {
        var helper = handle.attach(SqlHelper.class);
        long delayMillis = event.getPostDelaySeconds() * 1000L; // should be 180 days
        long nowMillis = Instant.now().toEpochMilli();

        log.info("Starting backfill with eventConfigId={}, delayMillis={}, nowMillis={}",
                event.getEventConfigurationId(), delayMillis, nowMillis);

        List<Candidate> candidates = helper.findNotCompletedOrNoQueuedEvent(studyDto.getId(), event.getEventConfigurationId());
        log.info("Found {} participants that are eligible for COMPLETED or does not have a queued event for it yet", candidates.size());

        List<Candidate> eligible = new ArrayList<>();
        List<Candidate> needQueuing = new ArrayList<>();
        for (var candidate : candidates) {
            long millisWhenCompletedShouldHappen = candidate.firstEnrolledAtMillis + delayMillis;
            if (millisWhenCompletedShouldHappen <= nowMillis) {
                eligible.add(candidate);
            } else {
                needQueuing.add(candidate);
            }
        }

        log.info("Number of eligible participants: {}", eligible.size());
        log.info("Number of participants that need a queued event: {}", needQueuing.size());
        System.out.print("Press enter to continue...");
        System.out.flush();
        new Scanner(System.in).nextLine();

        log.info("Queuing future events now...");
        var queueDao = handle.attach(QueuedEventDao.class);
        for (var candidate : needQueuing) {
            long millisWhenCompletedShouldHappen = candidate.firstEnrolledAtMillis + delayMillis;
            long millisLeft = millisWhenCompletedShouldHappen - nowMillis;
            if (millisLeft < 0) {
                throw new DDPException("Something is wrong! Calculated future delay is less than zero!");
            }
            int delaySecondsLeft = (int) (millisLeft / 1000);
            long queuedId = queueDao.addToQueue(event.getEventConfigurationId(),
                    candidate.userId, candidate.userId, delaySecondsLeft);
            log.info("Queued status change event for participant {} with queued_event_id={} after delay_seconds={}",
                    candidate.userId, queuedId, delaySecondsLeft);
        }

        log.info("Handling eligible participants now...");
        int batchSize = 100;
        int bufferSecs = 5 * 60; // 5 mins
        int delaySecsToUse = 0;
        // Since there's likely a large number of these, we partition into batches and spread them out 5 mins apart.
        for (var batch : ListUtils.partition(eligible, batchSize)) {
            delaySecsToUse += bufferSecs;
            for (var candidate : batch) {
                queueDao.addToQueue(event.getEventConfigurationId(), candidate.userId, candidate.userId, delaySecsToUse);
            }
            log.info("Prepared triggering of event for batch of size={} and delay_seconds={}", batch.size(), delaySecsToUse);
        }

        log.info("Finished setting backfill");
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select ptp.user_id, first_enrolled.first_enrolled_at"
                + "  from ("
                + "       select en.user_id, et.enrollment_status_type_code as current_status"
                + "       from user_study_enrollment as en"
                + "       join enrollment_status_type as et on et.enrollment_status_type_id = en.enrollment_status_type_id"
                + "       where en.study_id = :sid"
                + "       and en.valid_to is null) as ptp"
                + "  join ("
                + "       select en.user_id, min(en.valid_from) as first_enrolled_at"
                + "       from user_study_enrollment as en"
                + "       join enrollment_status_type as et on et.enrollment_status_type_id = en.enrollment_status_type_id"
                + "       where en.study_id = :sid"
                + "       and et.enrollment_status_type_code = 'ENROLLED'"
                + "       group by en.user_id) as first_enrolled on first_enrolled.user_id = ptp.user_id"
                + "  left join ("
                + "       select en.user_id, min(en.valid_from) as first_completed_at"
                + "       from user_study_enrollment as en"
                + "       join enrollment_status_type as et on et.enrollment_status_type_id = en.enrollment_status_type_id"
                + "       where en.study_id = :sid"
                + "       and et.enrollment_status_type_code = 'COMPLETED'"
                + "       group by en.user_id) as ever_completed on ever_completed.user_id = ptp.user_id"
                + "  left join ("
                + "       select participant_user_id as user_id, num_occurrences"
                + "       from event_configuration_occurrence_counter"
                + "       where event_configuration_id = :eid) as event_counter on event_counter.user_id = ptp.user_id"
                + "  left join ("
                + "       select participant_user_id as user_id, queued_event_id, post_after as delay_seconds"
                + "       from queued_event"
                + "       where event_configuration_id = :eid) as queued_event on queued_event.user_id = ptp.user_id"
                + " where ptp.current_status not in ('EXITED_BEFORE_ENROLLMENT', 'EXITED_AFTER_ENROLLMENT')"
                + "   and ever_completed.first_completed_at is null"
                + "   and queued_event.queued_event_id is null"
                + "   and (event_counter.num_occurrences is null or event_counter.num_occurrences = 0)")
        @RegisterConstructorMapper(Candidate.class)
        List<Candidate> findNotCompletedOrNoQueuedEvent(
                @Bind("sid") long studyId,
                @Bind("eid") long statusChangeEventId);
    }

    public static class Candidate {
        public final long userId;
        public final long firstEnrolledAtMillis;

        @JdbiConstructor
        public Candidate(@ColumnName("user_id") long userId, @ColumnName("first_enrolled_at") long firstEnrolledAtMillis) {
            this.userId = userId;
            this.firstEnrolledAtMillis = firstEnrolledAtMillis;
        }
    }
}

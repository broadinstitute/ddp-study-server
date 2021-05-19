package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlBatch;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off task to add event for setting enrollment status to COMPLETED and doing necessary data backfill.
 */
public class TestBostonCompletedStatusEvent implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonCompletedStatusEvent.class);
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

        EventConfiguration firstEnrolledEvent = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.USER_STATUS_CHANGE)
                .filter(event -> event.getEventActionType() == EventActionType.ENROLLMENT_COMPLETED)
                .findFirst().orElse(null);
        if (firstEnrolledEvent == null) {
            Config eventCfg = studyCfg.getConfigList("events").stream()
                    .filter(event -> "USER_STATUS_CHANGE".equals(event.getString("trigger.type")))
                    .filter(event -> "ENROLLMENT_COMPLETED".equals(event.getString("action.type")))
                    .findFirst().orElseThrow(() -> new DDPException("Could not find USER_STATUS_CHANGE event in study config"));
            eventBuilder.insertEvent(handle, eventCfg);

            int delaySeconds = eventCfg.getInt("delaySeconds");
            runBackfill(handle, studyDto, delaySeconds);
        } else {
            LOG.info("Already has USER_STATUS_CHANGE event configuration with id {}", firstEnrolledEvent.getEventConfigurationId());
        }
    }

    private void runBackfill(Handle handle, StudyDto studyDto, int delaySeconds) {
        var helper = handle.attach(SqlHelper.class);
        long delayMillis = delaySeconds * 1000L;
        long nowMillis = Instant.now().toEpochMilli();

        LOG.info("Starting backfill with delayMillis={} and nowMillis={}", delayMillis, nowMillis);

        List<Long> userIds = helper.findEligibleCompletedUsers(studyDto.getId(), delayMillis, nowMillis);
        LOG.info("Found {} participants that should be marked as COMPLETED", userIds.size());

        int[] counts = helper.terminateCurrentStatus(studyDto.getId(), nowMillis, userIds);
        DBUtils.checkUpdate(userIds.size(), Arrays.stream(counts).sum());
        LOG.info("Terminated current status for {} participants", userIds.size());

        long[] rowIds = helper.insertCompletedStatus(studyDto.getId(), nowMillis, userIds);
        DBUtils.checkInsert(userIds.size(), rowIds.length);
        LOG.info("Inserted new COMPLETED enrollment status for {} participants", userIds.size());

        LOG.info("Finished backfill");
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

        @SqlBatch("update user_study_enrollment set valid_to = :endMillis"
                + " where study_id = :studyId and user_id = :userId and valid_to is null")
        int[] terminateCurrentStatus(
                @Bind("studyId") long studyId,
                @Bind("endMillis") long endMillis,
                @Bind("userId") Iterable<Long> userIds);

        @GetGeneratedKeys
        @SqlBatch("insert into user_study_enrollment (study_id, user_id, enrollment_status_type_id, valid_from, valid_to)"
                + "select :studyId, :userId, enrollment_status_type_id, :startMillis, null"
                + "  from enrollment_status_type where enrollment_status_type_code = 'COMPLETED'")
        long[] insertCompletedStatus(
                @Bind("studyId") long studyId,
                @Bind("startMillis") long startMillis,
                @Bind("userId") Iterable<Long> userIds);
    }
}

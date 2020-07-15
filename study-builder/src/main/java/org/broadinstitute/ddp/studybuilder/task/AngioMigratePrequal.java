package org.broadinstitute.ddp.studybuilder.task;

import java.io.BufferedWriter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import com.opencsv.CSVWriter;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceStatusDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.ActivityInstanceStatusDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.WorkflowBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import org.jdbi.v3.core.mapper.reflect.JdbiConstructor;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Task to migrate Angio to prequal-as-activity design. Do not remove unless no longer needed.
 */
public class AngioMigratePrequal implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(AngioMigratePrequal.class);
    private static final String DATA_FILE = "patches/migrate-to-prequal.conf";

    private static final String ANGIO_STUDY = "ANGIO";
    private static final String ACT_PREQUAL = "PREQUAL";
    private static final String ACT_ABOUT_YOU = "ANGIOABOUTYOU";
    private static final String ACT_LOVED_ONE = "ANGIOLOVEDONE";
    private static final String Q_FIRST_NAME = "PREQUAL_FIRST_NAME";
    private static final String Q_LAST_NAME = "PREQUAL_LAST_NAME";
    private static final String Q_SELF_DESCRIBE = "PREQUAL_SELF_DESCRIBE";
    private static final String OPT_DIAGNOSED = "DIAGNOSED";
    private static final String OPT_LOVED_ONE = "LOVED_ONE";
    private static final int NUM_PREQUAL_TRANSITIONS = 3;
    private static final int NUM_PREQUAL_EVENTS = 4;
    private static final int NUM_REGISTERED_EVENTS = 2;

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        updateAboutYouFormType(handle, studyDto);
        insertPrequalActivity(handle, studyDto, adminUser.getUserId());

        // Do the data backfill before adding workflows or events so we avoid those side-effects.
        backfillPrequalData(handle, studyDto, adminUser);

        addPrequalWorkflowTransitions(handle, studyDto);
        addPrequalEvents(handle, studyDto, adminUser.getUserId());
        changeWelcomeEmailEvents(handle, studyDto, adminUser.getUserId());
    }

    private void updateAboutYouFormType(Handle handle, StudyDto studyDto) {
        int updated = handle.attach(SqlHelper.class).updateFormType(studyDto.getId(), ACT_ABOUT_YOU, FormType.GENERAL);
        if (updated != 1) {
            throw new DDPException("Could not update form type for " + ACT_ABOUT_YOU);
        }
        LOG.info("Updated {} form type to {}", ACT_ABOUT_YOU, FormType.GENERAL);
    }

    private void insertPrequalActivity(Handle handle, StudyDto studyDto, long adminUserId) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);

        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        Config definition = activityBuilder.readDefinitionConfig(dataCfg.getString("prequalActivityFilepath"));
        activityBuilder.insertActivity(handle, definition, timestamp);
    }

    private void addPrequalWorkflowTransitions(Handle handle, StudyDto studyDto) {
        List<? extends Config> transitions = dataCfg.getConfigList("prequalWorkflows");
        if (transitions.size() != NUM_PREQUAL_TRANSITIONS) {
            throw new DDPException("Expected " + NUM_PREQUAL_TRANSITIONS
                    + " sets of workflow transitions for prequal but got " + transitions.size());
        }

        WorkflowBuilder workflowBuilder = new WorkflowBuilder(cfg, studyDto);
        for (Config transitionCfg : transitions) {
            workflowBuilder.insertTransitionSet(handle, transitionCfg);
        }
    }

    private void addPrequalEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        List<? extends Config> events = dataCfg.getConfigList("prequalEvents");
        if (events.size() != NUM_PREQUAL_EVENTS) {
            throw new DDPException("Expected " + NUM_PREQUAL_EVENTS + " events for prequal but got " + events.size());
        }

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
    }

    private void changeWelcomeEmailEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        SqlHelper helper = handle.attach(SqlHelper.class);

        helper.disableWelcomeEmailEvent(studyDto.getId(), ACT_ABOUT_YOU, "participant welcome email");
        helper.disableWelcomeEmailEvent(studyDto.getId(), ACT_LOVED_ONE, "loved-one welcome email");

        List<? extends Config> registeredEvents = dataCfg.getConfigList("registeredEvents");
        if (registeredEvents.size() != NUM_REGISTERED_EVENTS) {
            throw new DDPException("Expected " + NUM_REGISTERED_EVENTS + " user-registered events but got " + registeredEvents.size());
        }

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : registeredEvents) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
    }

    private void backfillPrequalData(Handle handle, StudyDto studyDto, UserDto adminUser) {
        long now = Instant.now().toEpochMilli();
        long prequalActId = ActivityBuilder.findActivityId(handle, studyDto.getId(), ACT_PREQUAL);

        String[] headers = new String[] {
                "skipped", "user_guid", "user_id", "enrollment_id", "enrollment_status",
                "first_name", "last_name", "has_about_you", "has_loved_one",
                "prequal_instance_guid", "prequal_instance_created_at", "prequal_instance_completed_at",
                "first_name_answer_guid", "last_name_answer_guid", "self_describe_answer_guid"
        };
        List<String[]> rows = new ArrayList<>();

        SqlHelper helper = handle.attach(SqlHelper.class);
        ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
        ActivityInstanceStatusDao instanceStatusDao = handle.attach(ActivityInstanceStatusDao.class);

        AnswerDao answerDao = handle.attach(AnswerDao.class);

        LOG.info("Backfilling prequal data for existing users...");

        AtomicLong count = new AtomicLong();
        helper.findUsersForPrequalBackfilling(studyDto.getId(), ACT_ABOUT_YOU, ACT_LOVED_ONE).forEach(user -> {
            PicklistAnswer selfDescribeAnswer = new PicklistAnswer(null, Q_SELF_DESCRIBE, null, new ArrayList<>());
            if (user.hasBothAboutYouAndLovedOne()) {
                LOG.warn("User {} has both about-you and loved-one. Proceeding to backfill with {}", user.userGuid, OPT_DIAGNOSED);
                selfDescribeAnswer.getValue().add(new SelectedPicklistOption(OPT_DIAGNOSED));
            } else if (user.hasAboutYou) {
                selfDescribeAnswer.getValue().add(new SelectedPicklistOption(OPT_DIAGNOSED));
            } else if (user.hasLovedOne) {
                selfDescribeAnswer.getValue().add(new SelectedPicklistOption(OPT_LOVED_ONE));
            } else {
                LOG.warn("User {} does not have about-you or loved-one. Either it's in a bad state or is dummy user."
                        + " Please follow up! Skipping for now...", user.userGuid);
                rows.add(new String[] {
                        "1",
                        user.userGuid,
                        String.valueOf(user.userId),
                        String.valueOf(user.enrollmentId),
                        user.enrollmentStatus.name(),
                        user.firstName,
                        user.lastName,
                        "0", "0",
                        "", "", "", "", "", ""
                });
                return;
            }

            // Safe to use higher-level DAO since there should not be any side-effects with creation of prequal instance.
            ActivityInstanceDto instance = instanceDao.insertInstance(prequalActId, adminUser.getUserGuid(), user.userGuid,
                    InstanceStatusType.CREATED, null, now);

            TextAnswer firstNameAnswer = new TextAnswer(null, Q_FIRST_NAME, null, user.firstName);
            answerDao.createAnswer(user.userId, instance.getId(), firstNameAnswer);

            TextAnswer lastNameAnswer = new TextAnswer(null, Q_LAST_NAME, null, user.lastName);
            answerDao.createAnswer(user.userId, instance.getId(), lastNameAnswer);

            answerDao.createAnswer(user.userId, instance.getId(), selfDescribeAnswer);

            // There's a unique constraint on updated_at timestamp, so use a new one.
            ActivityInstanceStatusDto completedStatusDto = instanceStatusDao.insertStatus(instance.getId(), InstanceStatusType.COMPLETE,
                    Instant.now().toEpochMilli(), adminUser.getUserGuid());

            rows.add(new String[] {
                    "0",
                    user.userGuid,
                    String.valueOf(user.userId),
                    String.valueOf(user.enrollmentId),
                    user.enrollmentStatus.name(),
                    user.firstName,
                    user.lastName,
                    user.hasAboutYou ? "1" : "0",
                    user.hasLovedOne ? "1" : "0",
                    instance.getGuid(),
                    Instant.ofEpochMilli(instance.getCreatedAtMillis()).toString(),
                    Instant.ofEpochMilli(completedStatusDto.getUpdatedAt()).toString(),
                    firstNameAnswer.getAnswerGuid(),
                    lastNameAnswer.getAnswerGuid(),
                    selfDescribeAnswer.getAnswerGuid()
            });
            count.incrementAndGet();
        });

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX").withZone(ZoneOffset.UTC);
        String filename = String.format("angio_prequal_backfill_%s.csv", fmt.format(Instant.ofEpochMilli(now)));
        try (BufferedWriter output = Files.newBufferedWriter(Paths.get(filename))) {
            CSVWriter writer = new CSVWriter(output);
            writer.writeNext(headers, false);
            writer.writeAll(rows, false);
            writer.flush();
            writer.close();
        } catch (Exception e) {
            throw new DDPException("Error while writing results to csv file " + filename, e);
        }

        LOG.info("Backfilled prequal data for {} users", count);
        LOG.info("Prequal backfill results written to file {}", filename);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("UPDATE form_activity"
                + "    SET form_type_id = (SELECT form_type_id FROM form_type WHERE form_type_code = :type)"
                + "  WHERE study_activity_id = ("
                + "        SELECT study_activity_id"
                + "          FROM study_activity"
                + "         WHERE study_id = :studyId"
                + "           AND study_activity_code = :activityCode)")
        int updateFormType(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode, @Bind("type") FormType type);

        @SqlQuery("SELECT cfg.event_configuration_id,"
                + "       email_tmpl.notification_template_id"
                + "  FROM event_configuration AS cfg"
                + "  JOIN event_trigger AS tr ON tr.event_trigger_id = cfg.event_trigger_id"
                + "  JOIN event_trigger_type AS tr_type ON tr_type.event_trigger_type_id = tr.event_trigger_type_id"
                + "  JOIN activity_status_trigger AS act_tr ON act_tr.activity_status_trigger_id = tr.event_trigger_id"
                + "  JOIN study_activity AS activity ON activity.study_activity_id = act_tr.study_activity_id"
                + "  JOIN activity_instance_status_type AS status_type"
                + "       ON status_type.activity_instance_status_type_id = act_tr.activity_instance_status_type_id"
                + "  JOIN event_action AS act ON act.event_action_id = cfg.event_action_id"
                + "  JOIN event_action_type AS act_type ON act_type.event_action_type_id = act.event_action_type_id"
                + "  JOIN user_notification_event_action AS email_act ON email_act.user_notification_event_action_id = act.event_action_id"
                + "  JOIN user_notification_template AS email_tmpl"
                + "       ON email_tmpl.user_notification_event_action_id = email_act.user_notification_event_action_id"
                + " WHERE cfg.umbrella_study_id = :studyId"
                + "   AND activity.study_activity_code = :activityCode"
                + "   AND tr_type.event_trigger_type_code = 'ACTIVITY_STATUS'"
                + "   AND status_type.activity_instance_status_type_code = 'CREATED'"
                + "   AND act_type.event_action_type_code = 'NOTIFICATION'"
                + "   AND cfg.is_active = 1")
        @RegisterConstructorMapper(EventConfig.class)
        EventConfig findWelcomeEmailEventConfig(@Bind("studyId") long studyId, @Bind("activityCode") String activityCode);

        @SqlUpdate("UPDATE event_configuration"
                + "    SET max_occurrences_per_user = 0, dispatch_to_housekeeping = 0, is_active = 0"
                + "  WHERE event_configuration_id = :id")
        int _disableWelcomeEmailEventConfig(@Bind("id") long eventConfigurationId);

        @SqlUpdate("UPDATE notification_template"
                + "    SET template_key = 'DEPRECATED. DO NOT SEND.'"
                + "  WHERE notification_template_id = :id")
        int _disableWelcomeEmailTemplate(@Bind("id") long notificationTemplateId);

        default void disableWelcomeEmailEvent(long studyId, String activityCode, String name) {
            EventConfig oldEvent = findWelcomeEmailEventConfig(studyId, activityCode);
            int numChanged = _disableWelcomeEmailEventConfig(oldEvent.eventConfigurationId);
            if (numChanged != 1) {
                throw new DDPException("Expected to disable one " + name + " event with id="
                        + oldEvent.eventConfigurationId + " but changed " + numChanged);
            }
            numChanged = _disableWelcomeEmailTemplate(oldEvent.notificationTemplateId);
            if (numChanged != 1) {
                throw new DDPException("Expected to disable one " + name + " event template with id="
                        + oldEvent.notificationTemplateId + " but changed " + numChanged);
            }
            LOG.info("Disabled " + name + " event with id={} notificationTemplateId={}",
                    oldEvent.eventConfigurationId, oldEvent.notificationTemplateId);
        }

        @SqlQuery("SELECT enroll.user_study_enrollment_id AS enrollment_id,"
                + "       etype.enrollment_status_type_code AS enrollment_status,"
                + "       u.user_id,"
                + "       u.guid AS user_guid,"
                + "       COALESCE(up.first_name, '') AS first_name,"
                + "       COALESCE(up.last_name, '') AS last_name,"
                + "       (SELECT 1 FROM activity_instance AS ai"
                + "          JOIN study_activity AS act ON act.study_activity_id = ai.study_activity_id"
                + "         WHERE act.study_id = enroll.study_id AND act.study_activity_code = :aboutYou AND ai.participant_id = u.user_id"
                + "         LIMIT 1"
                + "       ) AS has_about_you,"
                + "       (SELECT 1 FROM activity_instance AS ai"
                + "          JOIN study_activity AS act ON act.study_activity_id = ai.study_activity_id"
                + "         WHERE act.study_id = enroll.study_id AND act.study_activity_code = :lovedOne AND ai.participant_id = u.user_id"
                + "         LIMIT 1"
                + "       ) as has_loved_one"
                + "  FROM user_study_enrollment AS enroll"
                + "  JOIN enrollment_status_type AS etype ON etype.enrollment_status_type_id = enroll.enrollment_status_type_id"
                + "  JOIN user AS u ON u.user_id = enroll.user_id"
                + "  JOIN user_profile AS up ON up.user_id = u.user_id"
                + " WHERE enroll.study_id = :studyId"
                + "   AND enroll.valid_to is null")
        @RegisterConstructorMapper(UserInfo.class)
        Stream<UserInfo> findUsersForPrequalBackfilling(@Bind("studyId") long studyId,
                                                        @Bind("aboutYou") String aboutYouCode,
                                                        @Bind("lovedOne") String lovedOneCode);
    }

    public static class EventConfig {
        final long eventConfigurationId;
        final long notificationTemplateId;

        @JdbiConstructor
        public EventConfig(@ColumnName("event_configuration_id") long eventConfigurationId,
                           @ColumnName("notification_template_id") long notificationTemplateId) {
            this.eventConfigurationId = eventConfigurationId;
            this.notificationTemplateId = notificationTemplateId;
        }
    }

    // Container used for backfilling prequal data. `public` because JDBI needs access to this.
    public static class UserInfo {
        final long enrollmentId;
        final EnrollmentStatusType enrollmentStatus;
        final long userId;
        final String userGuid;
        final String firstName;
        final String lastName;
        final boolean hasAboutYou;
        final boolean hasLovedOne;

        @JdbiConstructor
        public UserInfo(@ColumnName("enrollment_id") long enrollmentId,
                        @ColumnName("enrollment_status") EnrollmentStatusType enrollmentStatus,
                        @ColumnName("user_id") long userId,
                        @ColumnName("user_guid") String userGuid,
                        @ColumnName("first_name") String firstName,
                        @ColumnName("last_name") String lastName,
                        @ColumnName("has_about_you") boolean hasAboutYou,
                        @ColumnName("has_loved_one") boolean hasLovedOne) {
            this.enrollmentId = enrollmentId;
            this.enrollmentStatus = enrollmentStatus;
            this.userId = userId;
            this.userGuid = userGuid;
            this.firstName = firstName;
            this.lastName = lastName;
            this.hasAboutYou = hasAboutYou;
            this.hasLovedOne = hasLovedOne;
        }

        boolean hasBothAboutYouAndLovedOne() {
            return hasAboutYou && hasLovedOne;
        }
    }
}

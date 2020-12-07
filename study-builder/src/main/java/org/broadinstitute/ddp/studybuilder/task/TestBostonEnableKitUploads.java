package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.DsmNotificationTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.NotificationEventAction;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One-off task to update configurations and events to support kit uploads and adhoc survey in deployed environments.
 */
public class TestBostonEnableKitUploads implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(TestBostonEnableKitUploads.class);
    private static final String STUDY_GUID = "testboston";
    private static final String RESULT_REPORT_FILE = "result-report.conf";

    // This snippet of PEX expression is used in event cancel expressions to limit triggering.
    private static final String KIT_REASON_PEX = "!user.event.kit.isReason(\"NORMAL\", \"REPLACEMENT\")";

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
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, adminUser.getId());
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        updateKitEventExpressions(handle, events);
        updateResultReportNamingDetails(handle, studyDto, activityBuilder);
        enableDisplayOfAdhocSymptomActivity(handle, studyDto);
        addAdhocSymptomStudyStaffEmail(handle, studyDto, events, eventBuilder);
    }

    private void updateKitEventExpressions(Handle handle, List<EventConfiguration> events) {
        LOG.info("Checking cancel expressions for kit events...");
        var helper = handle.attach(SqlHelper.class);
        var jdbiExpr = handle.attach(JdbiExpression.class);

        List<EventConfiguration> dsmEvents = events.stream()
                .filter(e -> e.getEventTriggerType() == EventTriggerType.DSM_NOTIFICATION)
                .sorted(Comparator.comparing(EventConfiguration::getEventConfigurationId))
                .collect(Collectors.toList());
        LOG.info("Found {} DSM_NOTIFICATION event configurations", dsmEvents.size());

        for (var dsmEvent : dsmEvents) {
            long eventId = dsmEvent.getEventConfigurationId();
            DsmNotificationEventType dsmType = ((DsmNotificationTrigger) dsmEvent.getEventTrigger()).getDsmEventType();
            LOG.info("Working on event with id={} dsmNotificationType={}...", eventId, dsmType);

            if (dsmType == DsmNotificationEventType.TESTBOSTON_SENT) {
                LOG.info("  Sent event will only be handled for initial kit, so skip adding kit reason clause");
                continue;
            } else if (dsmType == DsmNotificationEventType.TEST_RESULT) {
                LOG.info("  Test result event should trigger for all kit events, so skip adding kit reason clause");
                continue;
            }

            Long cancelExprId = helper.findEventCancelExprId(eventId).orElse(null);
            if (cancelExprId == null) {
                LOG.info("  Event does not have a cancel expression, so adding one...");
                Expression expr = jdbiExpr.insertExpression(KIT_REASON_PEX);
                DBUtils.checkUpdate(1, helper.updateEventCancelExprId(eventId, expr.getId()));
                LOG.info("  Added expression with id={} text=`{}`", expr.getId(), expr.getText());
            } else {
                LOG.info("  Event already has a cancel expression with id={}, checking if it has kit reason clause...", cancelExprId);
                Expression expr = jdbiExpr.getById(cancelExprId).get();
                if (expr.getText().startsWith(KIT_REASON_PEX)) {
                    LOG.info("  Cancel expression already contains kit reason clause: `{}`", expr.getText());
                } else {
                    // DSM event does not have the "reason" clause! Let's add it.
                    LOG.info("  Cancel expression does not have kit reason clause, updating it...");
                    String newPEX = String.format("%s || %s", KIT_REASON_PEX, expr.getText());
                    DBUtils.checkUpdate(1, jdbiExpr.updateById(expr.getId(), newPEX));
                    LOG.info("  Cancel expression updated: `{}`", newPEX);
                }
            }
        }
    }

    private void updateResultReportNamingDetails(Handle handle, StudyDto studyDto, ActivityBuilder activityBuilder) {
        LOG.info("Updating naming details for test result report activity...");

        Config defCfg = activityBuilder.readDefinitionConfig(RESULT_REPORT_FILE);
        LOG.info("Loaded activity definition from: {}", RESULT_REPORT_FILE);

        String activityCode = defCfg.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        String versionTag = defCfg.getString("versionTag");
        ActivityVersionDto versionDto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                .orElseThrow(() -> new DDPException("Could not find version " + versionTag));

        LOG.info("Comparing naming details for activityCode={} activityId={} ...", activityCode, activityId);
        var task = new UpdateActivityBaseSettings();
        task.init(cfgPath, studyCfg, varsCfg);
        task.compareNamingDetails(handle, defCfg, activityId, versionDto);
    }

    private void enableDisplayOfAdhocSymptomActivity(Handle handle, StudyDto studyDto) {
        String activityCode = varsCfg.getString("id.act.adhoc_symptom");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        handle.attach(JdbiActivity.class).updateExcludeFromDisplayById(activityId, false);
        LOG.info("Enabled dashboard display for activityCode={} activityId={}", activityCode, activityId);
    }

    private void addAdhocSymptomStudyStaffEmail(Handle handle, StudyDto studyDto,
                                                List<EventConfiguration> events, EventBuilder eventBuilder) {
        LOG.info("Checking adhoc symptom study staff email...");
        String activityCode = varsCfg.getString("id.act.adhoc_symptom");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        EventConfiguration emailEvent = null;
        for (var event : events) {
            if (event.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS) {
                var trigger = (ActivityStatusChangeTrigger) event.getEventTrigger();
                if (trigger.getStudyActivityId() == activityId && trigger.getInstanceStatusType() == InstanceStatusType.COMPLETE) {
                    if (event.getEventActionType() == EventActionType.NOTIFICATION) {
                        var action = (NotificationEventAction) event.getEventAction();
                        if (action.getNotificationType() == NotificationType.STUDY_EMAIL) {
                            emailEvent = event;
                            break;
                        }
                    }
                }
            }
        }

        if (emailEvent != null) {
            LOG.info("Already has study staff email event config with id={}", emailEvent.getEventConfigurationId());
        } else {
            LOG.info("Did not find study staff email event, creating...");
            Config emailEventCfg = null;
            for (var eventCfg : studyCfg.getConfigList("events")) {
                Config triggerCfg = eventCfg.getConfig("trigger");
                Config actionCfg = eventCfg.getConfig("action");
                if (triggerCfg.getString("type").equals("ACTIVITY_STATUS")
                        && triggerCfg.getString("activityCode").equals(activityCode)
                        && triggerCfg.getString("statusType").equals("COMPLETE")
                        && actionCfg.getString("type").equals("STUDY_EMAIL")) {
                    emailEventCfg = eventCfg;
                    break;
                }
            }
            if (emailEventCfg == null) {
                throw new DDPException("Could not find adhoc symptom study staff email event in study config file!");
            }
            eventBuilder.insertEvent(handle, emailEventCfg);
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select cancel_expression_id from event_configuration where event_configuration_id = :id")
        Optional<Long> findEventCancelExprId(@Bind("id") long eventConfigurationId);

        @SqlUpdate("update event_configuration set cancel_expression_id = :exprId where event_configuration_id = :id")
        int updateEventCancelExprId(@Bind("id") long eventConfigurationId, @Bind("exprId") long expressionId);
    }
}

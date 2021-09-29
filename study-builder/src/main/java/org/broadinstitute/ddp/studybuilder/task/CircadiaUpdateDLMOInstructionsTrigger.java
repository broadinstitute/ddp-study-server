package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.EventTriggerSql;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.dsm.DsmNotificationEventType;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.DsmNotificationTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One-off task to update configurations and events to support kit uploads and adhoc survey in deployed environments.
 */
public class CircadiaUpdateDLMOInstructionsTrigger implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(CircadiaUpdateDLMOInstructionsTrigger.class);
    private static final String STUDY_GUID = "circadia";

    private long studyId;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        this.studyId = studyDto.getId();

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        updateDLMOInstructionsTrigger(handle, events);
    }

    private void updateDLMOInstructionsTrigger(Handle handle, List<EventConfiguration> events) {
        LOG.info("Looking for DLMO_INSTRUCTIONS creation event...");
        var helper = handle.attach(SqlHelper.class);

        long activityId = helper.findActivityIdByStudyIdAndCode(studyId, "DLMO_INSTRUCTIONS");
        long hlqActivityId = helper.findActivityIdByStudyIdAndCode(studyId, "HEALTH_AND_LIFESTYLE_QUESTIONNAIRE");

        List<EventConfiguration> dsmEvents = events.stream()
                .filter(e -> e.getEventTriggerType() == EventTriggerType.DSM_NOTIFICATION)
                .sorted(Comparator.comparing(EventConfiguration::getEventConfigurationId))
                .collect(Collectors.toList());

        for (var dsmEvent : dsmEvents) {
            long eventId = dsmEvent.getEventConfigurationId();
            DsmNotificationTrigger trigger = (DsmNotificationTrigger) dsmEvent.getEventTrigger();
            DsmNotificationEventType dsmType = trigger.getDsmEventType();
            if (dsmEvent.getEventAction() instanceof ActivityInstanceCreationEventAction
                    && dsmType == DsmNotificationEventType.CIRCADIA_SENT) {
                ActivityInstanceCreationEventAction ea = (ActivityInstanceCreationEventAction) dsmEvent.getEventAction();
                LOG.info("Working on event with id={} dsmNotificationType={}...", eventId, dsmType);

                if (ea.getStudyActivityId() == activityId) {
                    long newTriggerId = handle.attach(EventTriggerDao.class)
                            .insertStatusTrigger(hlqActivityId, InstanceStatusType.COMPLETE);
                    long oldTriggerId = helper.getEventTriggerId(dsmEvent.getEventConfigurationId());
                    helper.updateEventTrigger(dsmEvent.getEventConfigurationId(), newTriggerId);
                    helper.deleteDsmNotificationEventTriggerId(oldTriggerId);
                    handle.attach(EventTriggerSql.class).deleteBaseTriggerById(oldTriggerId);
                    LOG.info("New trigger {} was created for the action", newTriggerId);
                }
            }
        }
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("update event_configuration set event_trigger_id = :triggerId where event_configuration_id = :eventConfigId")
        int updateEventTrigger(@Bind("eventConfigId") long eventConfigId, @Bind("triggerId") long eventTriggerId);

        @SqlQuery("select event_trigger_id from event_configuration where event_configuration_id = :id")
        int getEventTriggerId(@Bind("id") long eventConfigId);

        @SqlUpdate("delete from dsm_notification_trigger where dsm_notification_trigger_id = :id")
        int deleteDsmNotificationEventTriggerId(@Bind("id") long triggerId);

        default long findActivityIdByStudyIdAndCode(long studyId, String activityCode) {
            return getHandle().attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyId, activityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity id for " + activityCode));
        }
    }
}

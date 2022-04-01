package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.ActivityInstanceCreationEventAction;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class TestBostonAddInstanceCreationEvent implements CustomTask {
    private static final String EVENT_DATA_FILE = "patches/adhoc-symptom-creation-event.conf";
    private static final String STUDY_GUID = "testboston";

    private Config studyCfg;
    private Config eventDataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(EVENT_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Event Data file is missing: " + file);
        }
        this.eventDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg).getConfigList("events").get(0);
    }

    @Override
    public void run(Handle handle) {
        var guid = studyCfg.getString("adminUser.guid");
        var adminUser = handle.attach(UserDao.class)
                .findUserByGuid(guid)
                .orElseThrow(() -> new DaoException("Could not find participant user with guid: " + guid));

        var studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        var eventBuilder = new EventBuilder(studyCfg, studyDto, adminUser.getId());

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        log.info("Searching for activity instance creation event copy configuration...");

        EventConfiguration existingEvent = null;

        for (var event : events) {
            if (isEventsEquals(handle, event, eventDataCfg)) {
                existingEvent = event;
            }
        }

        if (existingEvent != null) {
            log.info("Already has activity instance creation event configuration with id {}",
                    existingEvent.getEventConfigurationId());
        } else {
            eventBuilder.insertEvent(handle, eventDataCfg);
        }
    }

    private boolean isEventsEquals(Handle handle, EventConfiguration handleEventCfg, Config eventCfg) {
        if (eventCfg.hasPath("cancelExpr")) {
            if (!handleEventCfg.getCancelExpression().equals(eventCfg.getString("cancelExpr"))) {
                return false;
            }
        }
        if (eventCfg.hasPath("maxOccurrencesPerUser")) {
            if (handleEventCfg.getMaxOccurrencesPerUser() != eventCfg.getInt("maxOccurrencesPerUser")) {
                return false;
            }
        }
        if (eventCfg.hasPath("delaySeconds")) {
            if (handleEventCfg.getPostDelaySeconds() != eventCfg.getInt("delaySeconds")) {
                return false;
            }
        }
        if (eventCfg.hasPath("dispatchToHousekeeping")) {
            if (handleEventCfg.dispatchToHousekeeping() != eventCfg.getBoolean("dispatchToHousekeeping")) {
                return false;
            }
        }
        if (eventCfg.hasPath("order")) {
            if (handleEventCfg.getExecutionOrder() != eventCfg.getInt("order")) {
                return false;
            }
        }

        var trigger = (ActivityStatusChangeTrigger) handleEventCfg.getEventTrigger();
        var triggerType = trigger.getEventConfigurationDto().getEventTriggerType().name();
        var triggerStatusType = trigger.getInstanceStatusType().name();
        var triggerActivityCode = handle.attach(JdbiActivity.class)
                .queryActivityById(trigger.getStudyActivityId())
                .getActivityCode();

        if (!triggerType.equals(eventCfg.getConfig("trigger").getString("type"))) {
            return false;
        }
        if (!triggerActivityCode.equals(eventCfg.getConfig("trigger").getString("activityCode"))) {
            return false;
        }
        if (!triggerStatusType.equals(eventCfg.getConfig("trigger").getString("statusType"))) {
            return false;
        }

        var action = (ActivityInstanceCreationEventAction) handleEventCfg.getEventAction();
        var actionType = handleEventCfg.getEventActionType().name();
        var actionActivityCode = handle.attach(JdbiActivity.class)
                .queryActivityById(action.getStudyActivityId())
                .getActivityCode();

        if (!actionType.equals(eventCfg.getConfig("action").getString("type"))) {
            return false;
        }
        return actionActivityCode.equals(eventCfg.getConfig("action").getString("activityCode"));
    }
}

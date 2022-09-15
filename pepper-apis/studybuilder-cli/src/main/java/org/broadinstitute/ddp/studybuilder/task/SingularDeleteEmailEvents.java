package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.model.event.NotificationEventAction;
import org.broadinstitute.ddp.model.event.NotificationType;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class SingularDeleteEmailEvents implements CustomTask {
    private static final String DATA_FILE  = "patches/singular-delete-events.conf";
    private static final String STUDY_GUID  = "singular";

    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    public SingularDeleteEmailEvents() {
        super();
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        log.info("TASK:: SingularDeleteEmailEvents ");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        deleteEvents(handle, studyDto, user.getUserId());
    }

    private void deleteEvents(Handle handle, StudyDto studyDto, long userId) {
        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        log.info("Deleting events configuration...");
        List<? extends Config> events = dataCfg.getConfigList("events");
        for (Config eventCfg : events) {
            deleteEvent(handle, eventCfg, studyDto);
        }
        log.info("Events configuration has been removed in study {}", cfg.getString("study.guid"));
    }

    private void deleteEvent(Handle handle, Config eventCfg, StudyDto studyDto) {
        var helper = handle.attach(SqlHelper.class);
        Config triggerCfg = eventCfg.getConfig("trigger");
        String activityCode = triggerCfg.getString("activityCode");
        EventTriggerType type = EventTriggerType.valueOf(triggerCfg.getString("type"));
        List<EventConfiguration> allEvents = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());
        long activityId = helper.findActivityIdByStudyIdAndCode(studyDto.getId(), activityCode);
        InstanceStatusType instanceStatusType = InstanceStatusType.valueOf(triggerCfg.getString("statusType"));
        List<EventConfiguration> concreteEvents = allEvents.stream()
                .filter(e -> e.getEventTriggerType() == type)
                .filter(e -> Objects.nonNull(e.getPreconditionExpression()))
                .filter(e -> e.getPreconditionExpression().equals(eventCfg.getString("preconditionExpr")))
                .filter(e -> e.getEventTrigger() instanceof ActivityStatusChangeTrigger)
                .filter(e -> ((ActivityStatusChangeTrigger) e.getEventTrigger()).getStudyActivityId() == activityId)
                .filter(e -> ((ActivityStatusChangeTrigger) e.getEventTrigger()).getInstanceStatusType() == instanceStatusType)
                .filter(e -> e.getEventAction() instanceof NotificationEventAction)
                .filter(e -> ((NotificationEventAction) e.getEventAction()).getNotificationType() == NotificationType.EMAIL)
                .collect(Collectors.toList());
        log.info("events: {}", concreteEvents);
        if (concreteEvents.size() > 1) {
            throw new DDPException("Found more than 1 event configuration with specified filters: " + concreteEvents);
        }

        log.info("Deleting config events: " + concreteEvents);

        concreteEvents.stream().map(EventConfiguration::getEventConfigurationId)
                .forEach(helper::deleteEventConfOccurenceById);

        concreteEvents.stream().map(EventConfiguration::getEventConfigurationId)
                .forEach(helper::deleteEventById);
    }

    private interface SqlHelper extends SqlObject {
        @SqlUpdate("delete from event_configuration where event_configuration_id = :id")
        int deleteEventById(@Bind("id") long eventId);

        @SqlUpdate("delete from event_configuration_occurrence_counter where event_configuration_id = :id")
        int deleteEventConfOccurenceById(@Bind("id") long eventId);

        default long findActivityIdByStudyIdAndCode(long studyId, String activityCode) {
            return getHandle().attach(JdbiActivity.class)
                    .findIdByStudyIdAndCode(studyId, activityCode)
                    .orElseThrow(() -> new DDPException("Could not find activity id for " + activityCode));
        }
    }

}

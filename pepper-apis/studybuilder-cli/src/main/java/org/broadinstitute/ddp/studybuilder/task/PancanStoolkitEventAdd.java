package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.event.ActivityStatusChangeTrigger;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class PancanStoolkitEventAdd implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(PancanStoolkitEventAdd.class);
    private static final String STUDY_GUID = "cmi-pancan";
    private static final String DATA_FILE = "patches/stoolkit-sent-event.conf";

    private Config dataCfg;
    private Config studyCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.studyCfg = studyCfg;
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
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
        LOG.info("Starting to deactivate events of stoolkit initial creation.");

        deactivateEvents(handle, events);

        LOG.info("Deactivated events of stoolkit initial creation.");

        LOG.info("Adding new event for DSM stoolkit creation.");

        Config eventDataCfg = dataCfg.getConfigList("events").get(0);

        eventBuilder.insertEvent(handle, eventDataCfg);

        LOG.info("Added new event for DSM stoolkit creation.");
    }



    private void deactivateEvents(Handle handle, List<EventConfiguration> eventConfigurations) {
        var dao = handle.attach(JdbiEventConfiguration.class);
        var eventsToDeactivate = eventConfigurations.stream()
                .filter(e -> shouldDeactivateEvent(e, handle))
                        .collect(Collectors.toList());
        LOG.info("number of existing pancan STOOL_KIT events to deactivate: " + eventsToDeactivate.size());
        eventsToDeactivate.forEach(e -> {
            long id = e.getEventConfigurationId();
            dao.updateIsActiveById(id, false);
        });
    }

    private boolean shouldDeactivateEvent(EventConfiguration e, Handle handle) {
        var trigger = e.getEventTrigger();
        if (trigger instanceof ActivityStatusChangeTrigger) {
            var triggerActivityCode = handle.attach(JdbiActivity.class)
                    .queryActivityById(((ActivityStatusChangeTrigger)trigger).getStudyActivityId())
                    .getActivityCode();
            if ((triggerActivityCode.equals("RELEASE")
                    || triggerActivityCode.equals("RELEASE_MINOR"))) {
                return e.getEventTriggerType() == EventTriggerType.ACTIVITY_STATUS
                    && e.getEventActionType() == EventActionType.ACTIVITY_INSTANCE_CREATION
                    && (e.getPreconditionExpression() != null && e.getPreconditionExpression().contains("STOOL_KIT"))
                    && ((ActivityStatusChangeTrigger) trigger).getInstanceStatusType() == InstanceStatusType.COMPLETE
                    && e.getMaxOccurrencesPerUser() == 1;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}

package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class CircadiaUpdateInstanceCreationEvent implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(CircadiaUpdateInstanceCreationEvent.class);
    private static final String EVENT_DATA_FILE = "patches/consent-instance-creation-event.conf";
    private static final String STUDY_GUID = "circadia";

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

        var actionActivityCode = eventDataCfg.getConfig("action").getString("activityCode");
        var actionActivityDTO = handle.attach(JdbiActivity.class)
                .findActivityByStudyIdAndCode(studyDto.getId(), actionActivityCode)
                .orElseThrow(() -> new DaoException("Could not find activity with activity code: " + actionActivityCode));

        List<EventConfiguration> existingEvents = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId()).stream()
                    .filter(eventConfiguration ->
                            eventConfiguration.getEventActionType().name()
                                .equals(eventDataCfg.getConfig("action").getString("type"))
                            && eventConfiguration.getEventTrigger().getEventConfigurationDto()
                                    .getActivityInstanceCreationStudyActivityId().equals(actionActivityDTO.getActivityId()))
                    .collect(Collectors.toList());

        if (existingEvents.size() > 0) {
            for (EventConfiguration event: existingEvents) {
                LOG.info("Updating ACTIVITY_INSTANCE_CREATION event configuration with id {}. Setting status is_active=false",
                        event.getEventConfigurationId());
                DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class)
                        .updateIsActiveById(event.getEventConfigurationId(), false));
            }
        }

        LOG.info("New ACTIVITY_INSTANCE_CREATION event configuration has added wit id: {}",
                eventBuilder.insertEvent(handle, eventDataCfg));

    }

}

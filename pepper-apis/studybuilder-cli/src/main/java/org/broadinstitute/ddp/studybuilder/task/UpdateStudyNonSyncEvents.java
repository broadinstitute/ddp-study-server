package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public class UpdateStudyNonSyncEvents implements CustomTask {

    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        String studyGuid = cfg.getString("study.guid");
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));

        log.info("Starting to deactivate events of '{}' (id:{}).", studyGuid, studyDto.getId());

        long amountOfOldEvents = collectEvents(handle, studyDto)
                .stream()
                .filter(e -> !e.dispatchToHousekeeping() && e.getLabel() == null)
                .map(e -> handle.attach(JdbiEventConfiguration.class).updateIsActiveById(e.getEventConfigurationId(), false))
                .count();

        log.info("Number of EXISTING non sync events to DEACTIVATE: {}", amountOfOldEvents);
        log.info("Successfully deactivated non sync events of {}.", studyGuid);

        insertEvents(handle, studyDto, user.getUserId());
        log.info("Event configurations added for study {}", studyGuid);
    }

    private List<EventConfiguration> collectEvents(Handle handle, StudyDto studyDto) {
        var eventDao = handle.attach(EventDao.class);
        return eventDao.getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(e -> eventDao.getEventConfigurationDtoById(e.getEventConfigurationId()).isPresent())
                .collect(Collectors.toList());
    }

    private void insertEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        if (!cfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }

        log.info("Inserting events configuration...");
        List<? extends Config> events = cfg.getConfigList("events")
                .stream()
                .filter(eventCfg -> !eventCfg.getBoolean("dispatchToHousekeeping") && !eventCfg.hasPath("label"))
                .collect(Collectors.toList());
        log.info("Number of NEW non sync events to INSERT: {}", events.size());

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
    }
}

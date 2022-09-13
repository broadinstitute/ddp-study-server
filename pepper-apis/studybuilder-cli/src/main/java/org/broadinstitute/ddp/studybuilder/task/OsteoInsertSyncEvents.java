package org.broadinstitute.ddp.studybuilder.task;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class OsteoInsertSyncEvents extends InsertStudyEvents {
    public OsteoInsertSyncEvents() {
        super("CMI-OSTEO", "patches/osteo-new-sync-events.conf");
    }

    @Override
    public void run(Handle handle) {
        disableCurrentSyncEvents(handle);
        super.run(handle);
    }

    private void disableCurrentSyncEvents(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyGuid);
        long amountOfOldEvents = collectEvents(handle, studyDto)
                .stream()
                .filter(EventConfiguration::dispatchToHousekeeping)
                .map(e -> handle.attach(JdbiEventConfiguration.class).updateIsActiveById(e.getEventConfigurationId(), false))
                .count();

        log.info("Number of EXISTING sync events to DEACTIVATE: {}", amountOfOldEvents);
        log.info("Successfully deactivated sync events of {}.", studyGuid);
    }

    private List<EventConfiguration> collectEvents(Handle handle, StudyDto studyDto) {
        var eventDao = handle.attach(EventDao.class);
        return eventDao.getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(e -> eventDao.getEventConfigurationDtoById(e.getEventConfigurationId()).isPresent())
                .collect(Collectors.toList());
    }

}

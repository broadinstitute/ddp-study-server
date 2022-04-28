package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.broadinstitute.ddp.studybuilder.StudyBuilder;
import org.jdbi.v3.core.Handle;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
public class PrionWorkflowAndEventsUpdate implements CustomTask {

    private static final String STUDY_GUID = "PRION";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfg = studyCfg;
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        var eventDao = handle.attach(EventDao.class);

        StudyBuilder studyBuilder = new StudyBuilder(cfgPath, cfg, varsCfg);

        log.info("Starting to deactivate events of PRION.");
        List<EventConfiguration> events = eventDao.getAllEventConfigurationsByStudyId(studyDto.getId())
                .stream()
                .filter(e -> eventDao.getEventConfigurationDtoById(e.getEventConfigurationId()).isPresent())
                .collect(Collectors.toList());
        deactivateEvents(handle, events);
        studyBuilder.runEvents(handle);

        studyBuilder.updateWorkflow(handle);
    }

    private void deactivateEvents(Handle handle, List<EventConfiguration> eventConfigurations) {
        var dao = handle.attach(JdbiEventConfiguration.class);
        log.info("Number of existing PRION events to deactivate: " + eventConfigurations.size());
        eventConfigurations.forEach(e -> {
            long id = e.getEventConfigurationId();
            dao.updateIsActiveById(id, false);
        });
        log.info("Deactivated events of PRION.");
    }
}

package org.broadinstitute.ddp.studybuilder.task;

import java.nio.file.Path;
import java.util.List;

import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.EventDao;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.types.EventActionType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.event.EventConfiguration;
import org.jdbi.v3.core.Handle;

@Slf4j
public class TestBostonDisableKitPrepEmail implements CustomTask {
    private static final String STUDY_GUID = "testboston";

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

        List<EventConfiguration> events = handle.attach(EventDao.class)
                .getAllEventConfigurationsByStudyId(studyDto.getId());

        EventConfiguration kitPrepEmail = events.stream()
                .filter(event -> event.getEventTriggerType() == EventTriggerType.KIT_PREP)
                .filter(event -> event.getEventActionType() == EventActionType.NOTIFICATION)
                .findFirst().orElseThrow(() -> new DDPException("Could not find kit prep email event"));

        DBUtils.checkUpdate(1, handle.attach(JdbiEventConfiguration.class)
                .updateIsActiveById(kitPrepEmail.getEventConfigurationId(), false));
        log.info("Disabled kit prep email event with id {}", kitPrepEmail.getEventConfigurationId());
    }
}

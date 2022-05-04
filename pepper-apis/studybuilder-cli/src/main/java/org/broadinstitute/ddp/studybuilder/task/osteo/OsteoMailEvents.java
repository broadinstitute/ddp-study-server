package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class OsteoMailEvents implements CustomTask {
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_PATH = "patches/mail-event-updates.conf";

    private Config dataCfg;
    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfg = studyCfg;
        File file = cfgPath.getParent().resolve(DATA_PATH).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        UserDto user = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        insertEvents(handle, studyDto, user.getUserId());
    }

    private void insertEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        log.info("Inserting events configuration...");
        List<? extends Config> events = dataCfg.getConfigList("events");
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
        log.info("Events configuration has added in study {}", STUDY_GUID);
    }
}

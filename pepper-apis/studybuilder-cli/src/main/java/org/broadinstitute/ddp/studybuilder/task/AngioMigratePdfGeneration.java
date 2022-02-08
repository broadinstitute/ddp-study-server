package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.jdbi.v3.core.Handle;

/**
 * Task to insert new pdf generation event. Do no remove unless no longer needed.
 */
public class AngioMigratePdfGeneration implements CustomTask {

    private static final String ANGIO_STUDY = "ANGIO";
    private static final String DATA_FILE = "patches/release-pdf-generation-events.conf";

    private Config cfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(ANGIO_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " study!");
        }
        this.cfg = studyCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file);
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUser.getUserId());
        for (Config eventCfg : dataCfg.getConfigList("events")) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
    }
}

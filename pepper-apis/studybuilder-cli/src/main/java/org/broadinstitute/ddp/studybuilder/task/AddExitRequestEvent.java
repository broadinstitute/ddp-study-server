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

public class AddExitRequestEvent implements CustomTask {

    private static final String DATA_FILE = "patches/exit-request-event.conf";
    private static final String ANGIO_STUDY = "ANGIO";
    private static final String BRAIN_STUDY = "cmi-brain";

    private Config dataCfg;
    private Config cfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equals(ANGIO_STUDY) && !studyGuid.equals(BRAIN_STUDY)) {
            throw new DDPException("This task is only for the " + ANGIO_STUDY + " or " + BRAIN_STUDY + " studies!");
        }

        cfg = studyCfg;
    }

    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        Config event = dataCfg.getConfig("event");
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUser.getUserId());
        eventBuilder.insertEvent(handle, event);
    }
}

package org.broadinstitute.ddp.studybuilder.task;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddStudyActivities implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(AddStudyActivities.class);

    protected String studyGuid;
    protected String dataFile;
    protected Config dataCfg;
    protected Path cfgPath;
    protected Config cfg;
    protected Config varsCfg;

    public AddStudyActivities(String studyGuid, String dataFile) {
        this.studyGuid = studyGuid;
        this.dataFile = dataFile;
    }

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(studyGuid)) {
            throw new DDPException("This task is only for the " + studyGuid + " study!");
        }

        File file = cfgPath.getParent().resolve(dataFile).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {

        LOG.info("Executing AddStudyActivities task for study : {} ", studyGuid);

        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getUserId());

        Instant timestamp = ConfigUtil.getInstantIfPresent(dataCfg, "activityTimestamp");
        activityBuilder.insertActivities(handle, dataCfg, timestamp);
    }

}

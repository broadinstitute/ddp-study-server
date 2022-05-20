package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class OsteoNewActivities implements CustomTask {

    private static final String DATA_FILE = "patches/osteo-new-activities.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config varsCfg;
    private Config dataCfg;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.cfg = studyCfg;
        this.varsCfg = varsCfg;

        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }


    @Override
    public void run(Handle handle) {
        UserDto adminUser = handle.attach(JdbiUser.class).findByUserGuid(cfg.getString("adminUser.guid"));
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));
        insertActivities(handle, studyDto, adminUser.getUserId());
    }

    private void insertActivities(Handle handle, StudyDto studyDto, long adminUserId) {
        log.info("Inserting activity configuration...");

        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);

        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        List<? extends Config> activities = dataCfg.getConfigList("activityFilepath");
        for (Config activity : activities) {
            Config definition = activityBuilder.readDefinitionConfig(activity.getString("name"));
            List<Config> nested = new ArrayList<>();
            for (String nestedFilename : activity.getStringList("nested")) {
                Config nestedDef = activityBuilder.readDefinitionConfig(nestedFilename);
                nested.add(nestedDef);
            }
            activityBuilder.insertActivity(handle, definition, nested, timestamp);
            log.info("Activity configuration {} has been added in study {}", activity, STUDY_GUID);
        }
    }
}

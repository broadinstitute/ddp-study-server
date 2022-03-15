package org.broadinstitute.ddp.studybuilder.task;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.EventBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OsteoGermlineConsent implements CustomTask {
    private static final Logger LOG = LoggerFactory.getLogger(OsteoGermlineConsent.class);
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String DATA_FILE = "patches/addendum-new-activity.conf";

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

        insertActivity(handle, studyDto, adminUser.getUserId());
        insertEvents(handle, studyDto, adminUser.getUserId());

    }

    private void insertActivity(Handle handle, StudyDto studyDto, long adminUserId) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUserId);
        Instant timestamp = ConfigUtil.getInstantIfPresent(cfg, "activityTimestamp");
        List<? extends Config> activities = dataCfg.getConfigList("activityFilepath");

        for (Config activity : activities) {
            Config definition = activityBuilder.readDefinitionConfig(activity.getString("name"));
            List<Config> nestedcfg = new ArrayList<>();
            activityBuilder.insertActivity(handle, definition, nestedcfg, timestamp);
            LOG.info("Activity configuration {} has been added in study {}", activity, STUDY_GUID);
        }
    }

    private void insertEvents(Handle handle, StudyDto studyDto, long adminUserId) {
        if (!dataCfg.hasPath("events")) {
            throw new DDPException("There is no 'events' configuration.");
        }
        LOG.info("Inserting events configuration...");
        List<? extends Config> events = dataCfg.getConfigList("events");
        EventBuilder eventBuilder = new EventBuilder(cfg, studyDto, adminUserId);
        for (Config eventCfg : events) {
            eventBuilder.insertEvent(handle, eventCfg);
        }
        LOG.info("Events configuration has added in study {}", STUDY_GUID);
    }
}

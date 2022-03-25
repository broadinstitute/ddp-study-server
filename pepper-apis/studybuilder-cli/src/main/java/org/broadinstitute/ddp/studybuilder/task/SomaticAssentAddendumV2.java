package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;

public class SomaticAssentAddendumV2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(SomaticAssentAddendumV2.class);
    private static final String DATA_FILE = "patches/parent-consent-assent.conf";
    private static final String CMI_OSTEO = "CMI-OSTEO";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Config varsCfg;
    private String versionTag;
    private Gson gson;
    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);

        if (!studyCfg.getString("study.guid").equals(CMI_OSTEO)) {
            throw new DDPException("This task is only for the " + CMI_OSTEO + " study!");
        }

        cfg = studyCfg;
        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();
        this.timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        LanguageStore.init(handle);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        String consentAssent = dataCfg.getString("activityFilepath");
        Config consentAssentCfg = activityBuild(studyDto, adminUser, consentAssent);

        String assentAddendum = dataCfg.getString("sectionFilePath");
        Config assentAddendumCfg = activityBuild(studyDto, adminUser, assentAddendum);

        versionTag = consentAssentCfg.getString("versionTag");
        String activityCode = consentAssentCfg.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, "versionTag");
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, "v2", meta);
        long revisionId = version2.getRevId();

        var sectionDef = gson.fromJson(ConfigUtil.toJson(assentAddendumCfg), FormSectionDef.class);

        var sectionId = handle.attach(SectionBlockDao.class)
                .insertSection(activityId, sectionDef, revisionId);

        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);

        jdbiActSection.insert(activityId, sectionId, revisionId, 60);
    }

    private Config activityBuild(StudyDto studyDto, User adminUser, String activityCodeConf) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getId());
        Config config = activityBuilder.readDefinitionConfig(activityCodeConf);
        return config;
    }
}

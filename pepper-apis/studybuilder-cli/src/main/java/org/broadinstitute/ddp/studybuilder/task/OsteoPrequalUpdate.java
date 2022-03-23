package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class OsteoPrequalUpdate implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoPrequalUpdate.class);
    private static final String FILE = "patches/prequal-updates.conf";
    private static final String DATA_FILE = "prequal.conf";
    private static final String STUDY_GUID = "CMI-OSTEO";
    private static final String ACTIVITY_CODE = "PREQUAL";

    private Config dataCfg;
    private Config studyCfg;
    private Config cfg;
    private Gson gson;
    private Instant timestamp;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        File file = cfgPath.getParent().resolve(FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
        File filecfg = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!filecfg.exists()) {
            throw new DDPException("Data file is missing: " + filecfg);
        }
        this.cfg = ConfigFactory.parseFile(filecfg).resolveWith(varsCfg);
        this.studyCfg = studyCfg;
        this.gson = GsonUtil.standardGson();
        this.timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        long activityId = ActivityBuilder.findActivityId(handle, adminUser.getId(), ACTIVITY_CODE);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, ACTIVITY_CODE)
                .orElseThrow(() -> new DDPException("Could not find id for activity " + ACTIVITY_CODE + " and study id " + STUDY_GUID));

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                STUDY_GUID, ACTIVITY_CODE, "v2");
        RevisionMetadata meta = new RevisionMetadata(timestamp.toEpochMilli(), adminUser.getId(), reason);
        revisionPrequal(activityId, dataCfg, handle, meta);
        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        ActivityDao activityDao = handle.attach(ActivityDao.class);
        ActivityVersionDto currentActivityVerDto = activityDao.getJdbiActivityVersion().findByActivityCodeAndVersionTag(
                adminUser.getId(), ACTIVITY_CODE, "v1").get();
        updateTitleandName(activityId, dataCfg, handle);
    }

    private void revisionPrequal(long activityId, Config dataCfg, Handle handle, RevisionMetadata revision) {
        FormActivityDef activityDef = GsonUtil.standardGson().fromJson(ConfigUtil.toJson(cfg), FormActivityDef.class);
        insertBlock(handle, dataCfg, activityId, activityDef, revision);
    }

    private void updateTitleandName(long id, Config dataCfg, Handle handle) {
        String name = dataCfg.getConfigList("translatedNames").get(0).getString("text");
        String title = dataCfg.getConfigList("translatedTitles").get(0).getString("text");
        SqlHelper attach = handle.attach(SqlHelper.class);
        attach.updateNameandTitle(title, name, id);
    }

    private void insertBlock(Handle handle, Config dataCfg, long activityId, FormActivityDef def, RevisionMetadata revisionMetadata) {
        SectionBlockDao attach = handle.attach(SectionBlockDao.class);
        List<FormSectionDef> sections = def.getSections();
        Config config = dataCfg.getConfig("blocksInsert").getConfig("block");
        int sectionOrder = dataCfg.getConfig("blocksInsert").getInt("sectionOrder");
        int blockOrder = dataCfg.getConfig("blocksInsert").getInt("blockOrder");
        Long sectionId = sections.get(sectionOrder).getSectionId();
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(config), FormBlockDef.class);
        RevisionDto rdto = RevisionDto.fromStartMetadata(activityId, revisionMetadata);
        attach.addBlock(activityId, sectionId, blockOrder, blockDef, rdto);
    }

    private interface SqlHelper extends SqlObject {

        @SqlUpdate("update i18n_activity_detail set name = :name, title = :title where study_activity_id = :id")
        int updateNameandTitle(@Bind("title") String title, @Bind("name") String name, @Bind("id")long id);

    }
}

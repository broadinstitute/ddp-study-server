package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
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
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
public class CircadiaPostCollectionV2 implements CustomTask {
    private static final String DATA_FILE = "patches/post-collection-v2.conf";
    private static final String STUDY = "circadia";
    private static final String BLOCK_KEY = "blockNew";
    private static final String ACTIVITY_GUID = "POST_COLLECTION_QUESTIONNAIRE";
    private static final String NEW_BLOCKS = "new-blocks";
    private static final String SECTION_ORDER = "section_order";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        if (!studyCfg.getString("study.guid").equals(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
        this.cfgPath = cfgPath;
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(cfg.getString("study.guid"));

        String activityCode = dataCfg.getString("activityCode");
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        revisionPostCollection(handle, adminUser.getId(), studyDto, activityCode, versionTag, timestamp.toEpochMilli());
    }

    private void revisionPostCollection(Handle handle, long adminUserId, StudyDto studyDto,
                                        String activityCode, String versionTag, long timestamp) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp, adminUserId, reason);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, versionTag, meta);

        addBlocks(activityId, handle, meta, version2);
    }

    private void addBlocks(long activityId, Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_BLOCKS);
        for (Config config : configList) {
            addNewBlock(activityId, config, handle, meta, version2);
        }
    }

    private void addNewBlock(long activityId, Config config,
                             Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int sectionOrder = config.getInt(SECTION_ORDER);
        int order = config.getInt("order");
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY, ACTIVITY_GUID).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version2);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
        if (order != -1) {
            sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                    order, blockDef, revDto);
        } else {
            int displayOrder = currentSectionDef.getBlocks().size() * 10 + 10;
            sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(),
                    displayOrder, blockDef, revDto.getId());
        }
    }



    private interface SqlHelper extends SqlObject {
        /**
         * Find the content block that has the given body template text. Make sure it is from a block that belongs in the expected activity
         * (and thus the expected study). This is done using a `union` subquery to find all the top-level and nested block ids for the
         * activity and using that to match on the content block.
         */
        @SqlQuery("select bt.* from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + " where tmpl.template_text like :text"
                + "   and bt.block_id in (select fsb.block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                        where fafs.form_activity_id = :activityId"
                + "                        union"
                + "                       select bn.nested_block_id"
                + "                         from form_activity__form_section as fafs"
                + "                         join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + "                         join block_nesting as bn on bn.parent_block_id = fsb.block_id"
                + "                        where fafs.form_activity_id = :activityId)")
        @RegisterConstructorMapper(BlockContentDto.class)
        BlockContentDto findContentBlockByBodyText(@Bind("activityId") long activityId, @Bind("text") String bodyTemplateText);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);
    }

}

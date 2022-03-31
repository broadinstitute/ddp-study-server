package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
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
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

@Slf4j
public class CircadiaInstructions implements CustomTask {
    private static final String DATA_FILE = "patches/dlmo-instructions.conf";
    private static final String STUDY = "circadia";
    private static final String BLOCK_KEY = "blockNew";
    private static final String OLD_TEMPLATE_KEY = "old_search";
    private static final String ORDER = "order";
    private static final String ACTIVITY_GUID = "DLMO_INSTRUCTIONS";
    private static final String NEW_BLOCKS = "new-blocks";
    private static final String SECTION_ORDER = "section_order";
    private static final String BLOCK_UPDATES = "block-updates";
    private static final String DELETE_BLOCKS = "delete-blocks";
    private static final String TRANS_UPDATE = "trans-update";
    private static final String TRANS_UPDATE_OLD = "old_text";
    private static final String TRANS_UPDATE_NEW = "new_text";

    private Path cfgPath;
    private Config cfg;
    private Config dataCfg;
    private Instant timestamp;
    private String versionTag;
    private Gson gson;

    private JdbiActivityVersion jdbiVersion;

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

        jdbiVersion = handle.attach(JdbiActivityVersion.class);

        String activityCode = dataCfg.getString("activityCode");
        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        updateInstructions(handle, adminUser.getId(), studyDto, activityCode, versionTag, timestamp.toEpochMilli());
    }

    private void updateInstructions(Handle handle, long adminUserId, StudyDto studyDto,
                                    String activityCode, String versionTag, long timestamp) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata meta = new RevisionMetadata(timestamp, adminUserId, reason);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version = jdbiVersion.getActiveVersion(activityId).get();
        addBlocks(activityId, handle, meta, version);
        updateTemplates(handle, meta, version);
        deleteBlocks(handle, version);
        updateTranslationSummaries(handle, version);
    }

    private void updateTranslationSummaries(Handle handle, ActivityVersionDto version) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANS_UPDATE);
        for (Config config : configList) {
            updateSummary(config, handle, version);
        }
    }

    private void updateSummary(Config config, Handle handle, ActivityVersionDto version) {
        String oldSum = config.getString(TRANS_UPDATE_OLD);
        String newSum = config.getString(TRANS_UPDATE_NEW);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY, ACTIVITY_GUID).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version);

        currentDef.getTranslatedSummaries()
                .stream().filter(sum -> sum.getText().equals(oldSum))
                .forEach(sum -> handle.attach(SqlHelper.class).updateTransSummaryText(sum.getId().get(), newSum));
    }


    private void addBlocks(long activityId, Handle handle, RevisionMetadata meta, ActivityVersionDto version) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_BLOCKS);
        for (Config config : configList) {
            addNewBlock(activityId, config, handle, meta, version);
        }
    }

    private void addNewBlock(long activityId, Config config,
                             Handle handle, RevisionMetadata meta, ActivityVersionDto version) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int order = config.getInt(ORDER);
        int sectionOrder = config.getInt(SECTION_ORDER);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY, ACTIVITY_GUID).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(version.getRevId(), meta);
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void updateTemplates(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList(BLOCK_UPDATES);
        for (Config config : configList) {
            updateContentBlockTemplate(handle, meta, version2, config);
        }
    }

    private void updateContentBlockTemplate(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config conf) {
        Config config = conf.getConfig(BLOCK_KEY);
        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);
        Template newBodyTemplate = contentBlockDef.getBodyTemplate();
        String oldBlockTemplateText = conf.getString(OLD_TEMPLATE_KEY);

        String templateSearchParam = String.format("%s%s%s", "%", oldBlockTemplateText, "%");
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), templateSearchParam);

        handle.attach(SqlHelper.class).updateTemplateText(contentBlock.getBodyTemplateId(), newBodyTemplate.getTemplateText());
    }

    private void deleteBlocks(Handle handle, ActivityVersionDto version) {
        List<? extends Config> configList = dataCfg.getConfigList(DELETE_BLOCKS);
        for (Config config : configList) {
            deleteBlock(config, handle, version);
        }
    }

    private void deleteBlock(Config config, Handle handle, ActivityVersionDto version) {
        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);

        String templateSearchParam = String.format("%s%s%s", "%", contentBlockDef.getBodyTemplate().getTemplateText(), "%");
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(version.getActivityId(), templateSearchParam);

        handle.attach(SqlHelper.class)._deleteSectionBlockMembershipByBlockId(contentBlock.getBlockId());
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

        @SqlUpdate("update template set template_text = :text where template_id = :id")
        int _updateTemplateTextByTemplateId(@Bind("id") long templateId, @Bind("text") String templateText);

        default void updateTemplateText(long templateId, String templateText) {
            int numUpdated = _updateTemplateTextByTemplateId(templateId, templateText);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template text for templateId="
                        + templateId + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("update i18n_study_activity_summary_trans set translation_text = :text "
                + "where i18n_study_activity_summary_trans_id = :id")
        int _updateTransSummaryById(@Bind("id") long id, @Bind("text") String updateText);

        default void updateTransSummaryText(long id, String text) {
            int numUpdated = _updateTransSummaryById(id, text);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template text for templateId="
                        + id + " but updated " + numUpdated);
            }
        }

        @SqlUpdate("delete from form_section__block where block_id = :blockId")
        int _deleteSectionBlockMembershipByBlockId(@Bind("blockId") long blockId);
    }

}

package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.commons.collections.CollectionUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiTemplateVariable;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class OsteoParentConsent implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(OsteoParentConsent.class);
    private static final String DATA_FILE = "patches/self-consent.conf";
    private static final String STUDY = "CMI-OSTEO";
    private static final String BLOCK_KEY = "blockNew";
    private static final String OLD_TEMPLATE_KEY = "old_search";
    private static final String ORDER = "order";
    private static final String ACTIVITY_GUID = "CONSENT";
    private static final String NEW_BLOCKS = "new-blocks";
    private static final String NEW_NESTED_BLOCKS = "new-nested-blocks";
    private static final String SECTION_ORDER = "section_order";
    private static final String BLOCK_UPDATES = "block-updates";
    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String TRANSLATION_OLD = "oldValue";
    private static final String TRANSLATION_NEW = "newValue";

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
        LOG.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
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

        updateVariables(activityId, handle);
        updateTemplates(handle, meta, version);
        addNestedBlocks(activityId, handle, meta, version);
        addBlocks(activityId, handle, meta, version);
    }

    private void updateVariables(long activityId, Handle handle) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            updateVariableByText(activityId, config.getString(TRANSLATION_OLD), config.getString(TRANSLATION_NEW), handle);
        }
    }

    private void updateVariableByText(long activityId, String before, String after, Handle handle) {
        String oldValue = String.format("%s%s%s", "%", before, "%");
        List<Long> varIds = handle.attach(SqlHelper.class).findVariableIdsByText(activityId, oldValue);
        if (CollectionUtils.isEmpty(varIds)) {
            throw new DDPException("Could not find any variable with text " + before);
        }
        varIds.forEach(varId -> {
            handle.attach(SqlHelper.class).updateVarValueByTemplateVarId(varId, after);
            LOG.info("Template variable {} text was updated from \"{}\" to \"{}\"", varId, before, after);
        });
    }

    private void updateTemplates(Handle handle, RevisionMetadata meta, ActivityVersionDto version) {
        List<? extends Config> configList = dataCfg.getConfigList(BLOCK_UPDATES);
        for (Config config : configList) {
            updateContentBlockTemplate(handle, meta, version, config);
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

        for (TemplateVariable variable : contentBlockDef.getBodyTemplate().getVariables()) {
            insertVariable(handle, variable, contentBlock);
        }

        handle.attach(SqlHelper.class).updateTemplateText(contentBlock.getBodyTemplateId(), newBodyTemplate.getTemplateText());
    }

    private void insertVariable(Handle handle, TemplateVariable variable, BlockContentDto template) {
        var jdbiTemplateVariable = handle.attach(JdbiTemplateVariable.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);

        long variableId = jdbiTemplateVariable.insertVariable(template.getBodyTemplateId(), variable.getName());
        for (var translation : variable.getTranslations()) {
            String language = translation.getLanguageCode();
            jdbiVariableSubstitution.insert(language, translation.getText(), template.getRevisionId(), variableId);
        }
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
        int order = config.getInt(ORDER);
        int sectionOrder = config.getInt(SECTION_ORDER);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY, ACTIVITY_GUID).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version2);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void addNestedBlocks(long activityId, Handle handle, RevisionMetadata meta, ActivityVersionDto version) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_NESTED_BLOCKS);
        for (Config config : configList) {
            addNewNestedBlock(activityId, config, handle, meta, version);
        }
    }

    private void addNewNestedBlock(long activityId, Config config,
                             Handle handle, RevisionMetadata meta, ActivityVersionDto version) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int sectionOrder = config.getInt(SECTION_ORDER);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY, ACTIVITY_GUID).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        Optional<FormBlockDef> blockOpt = currentSectionDef.getBlocks()
                .stream()
                .filter(formBlockDef -> formBlockDef.getBlockType() == BlockType.GROUP)
                .findFirst();

        blockOpt.ifPresent(formBlockDef -> sectionBlockDao.insertNestedBlocks(activityId, formBlockDef.getBlockId(),
                List.of(blockDef), version.getRevId()));
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

        @SqlUpdate("update template set template_text = :text where template_id = :id")
        int _updateTemplateTextByTemplateId(@Bind("id") long templateId, @Bind("text") String templateText);

        default void updateTemplateText(long templateId, String templateText) {
            int numUpdated = _updateTemplateTextByTemplateId(templateId, templateText);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 template text for templateId="
                        + templateId + " but updated " + numUpdated);
            }
        }

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id"
                + " where ts.substitution_value like :text")
        List<Long> findVariableIdsByText(@Bind("activityId") long activityId,
                                         @Bind("text") String text);

        // For single language only
        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);
    }

}

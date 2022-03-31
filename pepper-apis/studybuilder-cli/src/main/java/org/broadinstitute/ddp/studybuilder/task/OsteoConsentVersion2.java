package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiFormActivityFormSection;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
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

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
public class OsteoConsentVersion2 implements CustomTask {
    private static final String DATA_FILE = "patches/consent-version-2.conf";
    private static final String DATA_FILE_SOMATIC_CONSENT_ADDENDUM = "patches/somatic-consent-addendum-val.conf";
    private static final String DATA_FILE_SOMATIC_ASSENT_ADDENDUM = "patches/parent-consent-assent.conf";
    private static final String OSTEO_STUDY = "CMI-OSTEO";

    private static final String DATA_FILE_OSTEO_SELF_CONSENT = "patches/self-consent.conf";
    private static final String DATA_FILE_OSTEO_CONSENT_ASSENT = "patches/consent-assent.conf";
    private static final String DATA_FILE_OSTEO_PARENTAL_CONSENT = "patches/parental-consent.conf";
    private static final String BLOCK_KEY = "blockNew";
    private static final String OLD_TEMPLATE_KEY = "old_search";
    private static final String ORDER = "order";
    private static final String NEW_BLOCKS = "new-blocks";
    private static final String NEW_NESTED_BLOCKS = "new-nested-blocks";
    private static final String SECTION_ORDER = "section_order";
    private static final String BLOCK_UPDATES = "block-updates";
    private static final String TRANSLATION_UPDATES = "translation-updates";
    private static final String ADULT_TRANSLATION_NEW = "newValue";
    private static final String ADULT_TRANSLATION_KEY = "variableName";

    private Config dataCfg;
    private Config somaticAddendumConsentCfg;
    private Config assentAddendumCfg;
    private Config selfConsentDataCfg;
    private Config parentalConsentDataCfg;
    private Config consentAssentDataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;

        File fileSomaticAddendum = cfgPath.getParent().resolve(DATA_FILE_SOMATIC_CONSENT_ADDENDUM).toFile();
        if (!fileSomaticAddendum.exists()) {
            throw new DDPException("Data file is missing: " + fileSomaticAddendum);
        }
        somaticAddendumConsentCfg = ConfigFactory.parseFile(fileSomaticAddendum);
        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

        File fileAssentAddendum = cfgPath.getParent().resolve(DATA_FILE_SOMATIC_ASSENT_ADDENDUM).toFile();
        if (!fileAssentAddendum.exists()) {
            throw new DDPException("Data file is missing: " + fileAssentAddendum);
        }
        assentAddendumCfg = ConfigFactory.parseFile(fileAssentAddendum);

        File selfConsentFile = cfgPath.getParent().resolve(DATA_FILE_OSTEO_SELF_CONSENT).toFile();
        if (!selfConsentFile.exists()) {
            throw new DDPException("Data file is missing: " + selfConsentFile);
        }
        selfConsentDataCfg = ConfigFactory.parseFile(selfConsentFile);

        File parentalConsentFile = cfgPath.getParent().resolve(DATA_FILE_OSTEO_PARENTAL_CONSENT).toFile();
        if (!parentalConsentFile.exists()) {
            throw new DDPException("Data file is missing: " + parentalConsentFile);
        }
        parentalConsentDataCfg = ConfigFactory.parseFile(parentalConsentFile);

        File consentAssentFile = cfgPath.getParent().resolve(DATA_FILE_OSTEO_CONSENT_ASSENT).toFile();
        if (!consentAssentFile.exists()) {
            throw new DDPException("Data file is missing: " + consentAssentFile);
        }
        consentAssentDataCfg = ConfigFactory.parseFile(consentAssentFile);

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
        gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCodeConsentAssent = "CONSENT_ASSENT";
        String activityCodeConsent = "CONSENT";
        String activityCodeParentalConsent = "PARENTAL_CONSENT";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));

        log.info("Changing version of {} to {} with timestamp={}", activityCodeConsent, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reasonConsentAssent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsentAssent, versionTag);
        RevisionMetadata metaConsentAssent = new RevisionMetadata(ts, adminUser.getId(), reasonConsentAssent);

        String reasonConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeConsent, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reasonConsent);

        String reasonParentalConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeParentalConsent, versionTag);
        RevisionMetadata metaParentalConsent = new RevisionMetadata(ts, adminUser.getId(), reasonParentalConsent);

        ActivityVersionDto version2ForConsentAssent = getVersion2(handle, studyDto, metaConsentAssent, activityCodeConsentAssent);
        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCodeConsent);
        ActivityVersionDto version2ForParentalConsent = getVersion2(handle, studyDto, metaParentalConsent, activityCodeParentalConsent);

        updateVariables(handle, metaConsentAssent, version2ForConsentAssent);
        runSomaticConsentAddendum(handle, adminUser, studyDto, version2ForConsent, version2ForConsentAssent, version2ForParentalConsent);
        runSomaticAssentAddendum(handle, adminUser, studyDto, version2ForConsentAssent);
        runAdultConsentUpdate(handle, metaConsent, studyDto, activityCodeConsent, version2ForConsent);
        runParentalConsentUpdate(handle, metaParentalConsent, studyDto, activityCodeParentalConsent, version2ForParentalConsent);
        runConsentAssentUpdate(handle, metaConsentAssent, studyDto, activityCodeConsentAssent, version2ForConsentAssent);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        ActivityVersionDto version2 = handle.attach(ActivityDao.class).changeVersion(activityId, "v2", meta);
        return version2;
    }

    private void updateVariables(Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        List<? extends Config> configList = dataCfg.getConfigList("translation-updates");
        System.out.println(configList);
        for (Config config : configList) {
            revisionVariableTranslation(config.getString(TRANSLATION_KEY), config.getString(TRANSLATION_NEW), handle, meta, version2);
        }
    }

    private static final String TRANSLATION_KEY = "varName";
    private static final String TRANSLATION_NEW = "newValue";

    private void revisionVariableTranslation(String varName, String newTemplateText, Handle handle,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        long tmplVarId = handle.attach(SqlHelper.class).findTemplateVariableIdByVariableName(varName);
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newVarSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newVarSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
    }

    public void runSomaticConsentAddendum(Handle handle, User adminUser, StudyDto studyDto,
                                          ActivityVersionDto version2Consent, ActivityVersionDto version2ConsentAssent,
                                          ActivityVersionDto version2ParentalConsent) {
        LanguageStore.init(handle);

        String filePath = somaticAddendumConsentCfg.getConfigList("updates").get(0).getString("activityFilePath");
        Config consentAssent = activityBuild(studyDto, adminUser, filePath);

        String sectionfilepath = somaticAddendumConsentCfg.getConfigList("updates").get(0).getString("sectionFilePath");
        Config consentAddendumPediatric = activityBuild(studyDto, adminUser, sectionfilepath);

        insertSection(studyDto, handle, consentAddendumPediatric, consentAssent, version2ConsentAssent);

        String filePath1 = somaticAddendumConsentCfg.getConfigList("updates").get(1).getString("activityFilePath");
        Config consentSelf = activityBuild(studyDto, adminUser, filePath1);

        String sectionfilepath1 = somaticAddendumConsentCfg.getConfigList("updates").get(1).getString("sectionFilePath");
        Config consentAddendumSelf = activityBuild(studyDto, adminUser, sectionfilepath1);

        insertSection(studyDto, handle, consentAddendumSelf, consentSelf, version2Consent);

        String filePath2 = somaticAddendumConsentCfg.getConfigList("updates").get(2).getString("activityFilePath");
        Config consentParental = activityBuild(studyDto, adminUser, filePath2);

        String sectionfilepath2 = somaticAddendumConsentCfg.getConfigList("updates").get(2).getString("sectionFilePath");
        Config parentalConsent = activityBuild(studyDto, adminUser, sectionfilepath2);

        insertSection(studyDto, handle, parentalConsent, consentParental, version2ParentalConsent);

    }


    public void runSomaticAssentAddendum(Handle handle, User adminUser,
                                         StudyDto studyDto, ActivityVersionDto version2ConsentAssent) {
        LanguageStore.init(handle);

        String consentAssent = assentAddendumCfg.getString("activityFilepath");
        Config consentAssentCfg = activityBuild(studyDto, adminUser, consentAssent);

        String assentAddendum = assentAddendumCfg.getString("sectionFilePath");
        Config assentAddendumCfg = activityBuild(studyDto, adminUser, assentAddendum);

        versionTag = consentAssentCfg.getString("versionTag");
        String activityCode = consentAssentCfg.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        long revisionId = version2ConsentAssent.getRevId();

        var sectionDef = gson.fromJson(ConfigUtil.toJson(assentAddendumCfg), FormSectionDef.class);

        var sectionId = handle.attach(SectionBlockDao.class)
                .insertSection(activityId, sectionDef, revisionId);

        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);

        jdbiActSection.insert(activityId, sectionId, revisionId, 60);
    }

    private void insertSection(StudyDto studyDto, Handle handle,
                               Config section, Config activity, ActivityVersionDto version2) {

        String activityCode = activity.getString("activityCode");
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        long revisionId = version2.getRevId();

        var sectionDef = gson.fromJson(ConfigUtil.toJson(section), FormSectionDef.class);

        var sectionId = handle.attach(SectionBlockDao.class)
                .insertSection(activityId, sectionDef, revisionId);

        var jdbiActSection = handle.attach(JdbiFormActivityFormSection.class);

        jdbiActSection.insert(activityId, sectionId, revisionId, 50);
    }

    private Config activityBuild(StudyDto studyDto, User adminUser, String activityCodeConf) {
        ActivityBuilder activityBuilder = new ActivityBuilder(cfgPath.getParent(), cfg, varsCfg, studyDto, adminUser.getId());
        Config config = activityBuilder.readDefinitionConfig(activityCodeConf);
        return config;
    }

    private void runConsentAssentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                          String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        updateAdultTemplates(handle, meta, version2, consentAssentDataCfg);
        addAdultNestedBlocks(activityId, handle, "CONSENT_ASSENT", version2, consentAssentDataCfg);
        addAdultBlocks(activityId, handle, "CONSENT_ASSENT", meta, version2, consentAssentDataCfg);
    }

    private void runParentalConsentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                       String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        updateAdultTemplates(handle, meta, version2, parentalConsentDataCfg);
        addAdultNestedBlocks(activityId, handle, "PARENTAL_CONSENT", version2, parentalConsentDataCfg);
        addAdultBlocks(activityId, handle, "PARENTAL_CONSENT", meta, version2, parentalConsentDataCfg);
    }

    private void runAdultConsentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                       String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        updateAdultVariables(handle, meta, version2, selfConsentDataCfg);
        updateAdultTemplates(handle, meta, version2, selfConsentDataCfg);
        addAdultNestedBlocks(activityId, handle, "CONSENT", version2, selfConsentDataCfg);
        addAdultBlocks(activityId, handle, "CONSENT", meta, version2, selfConsentDataCfg);
    }

    private void updateAdultVariables(Handle handle, RevisionMetadata meta,
                                      ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(TRANSLATION_UPDATES);
        for (Config config : configList) {
            revisionAdultVariableTranslation(config.getString(ADULT_TRANSLATION_KEY),
                    config.getString(ADULT_TRANSLATION_NEW), handle, meta, version2);
        }
    }


    private void revisionAdultVariableTranslation(String varName, String newTemplateText, Handle handle,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        long tmplVarId = handle.attach(SqlHelper.class).findTemplateVariableIdByVariableName(varName);
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
    }

    private void updateAdultTemplates(Handle handle, RevisionMetadata meta,
                                      ActivityVersionDto version, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(BLOCK_UPDATES);
        for (Config config : configList) {
            revisionContentBlockTemplate(handle, meta, version, config);
        }
    }

    private void revisionContentBlockTemplate(Handle handle, RevisionMetadata meta,
                                              ActivityVersionDto versionDto, Config conf) {
        Config config = conf.getConfig(BLOCK_KEY);
        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(config), ContentBlockDef.class);
        Template newBodyTemplate = contentBlockDef.getBodyTemplate();
        Template newTitleTemplate = contentBlockDef.getTitleTemplate();

        String oldBlockTemplateText = conf.getString(OLD_TEMPLATE_KEY);

        JdbiBlockContent jdbiBlockContent = handle.attach(JdbiBlockContent.class);

        String templateSearchParam = String.format("%s%s%s", "%", oldBlockTemplateText, "%");
        BlockContentDto contentBlock = handle.attach(SqlHelper.class)
                .findContentBlockByBodyText(versionDto.getActivityId(), templateSearchParam);


        JdbiRevision jdbiRevision = handle.attach(JdbiRevision.class);
        long newRevId = jdbiRevision.copyAndTerminate(contentBlock.getRevisionId(), meta);
        int numUpdated = jdbiBlockContent.updateRevisionById(contentBlock.getId(), newRevId);
        if (numUpdated != 1) {
            throw new DDPException(String.format(
                    "Unable to terminate active block_content with id=%d, blockId=%d, bodyTemplateId=%d",
                    contentBlock.getId(), contentBlock.getBlockId(), contentBlock.getBodyTemplateId()));
        }

        TemplateDao templateDao = handle.attach(TemplateDao.class);
        templateDao.disableTemplate(contentBlock.getBodyTemplateId(), meta);
        if (contentBlock.getTitleTemplateId() != null) {
            templateDao.disableTemplate(contentBlock.getTitleTemplateId(), meta);
        }
        Long newBodyTemplateId = templateDao.insertTemplate(newBodyTemplate, versionDto.getRevId());

        Long newTitleTemplateId = null;
        if (newTitleTemplate != null) {
            newTitleTemplateId = templateDao.insertTemplate(newTitleTemplate, versionDto.getRevId());
        }

        long newBlockContentId = jdbiBlockContent.insert(contentBlock.getBlockId(), newBodyTemplateId,
                newTitleTemplateId, versionDto.getRevId());

        log.info("Created block_content with id={}, blockId={}, bodyTemplateId={} for bodyTemplateText={}",
                newBlockContentId, contentBlock.getBlockId(), newBodyTemplateId, contentBlockDef.getBodyTemplate().getTemplateText());
    }

    private void addAdultBlocks(long activityId, Handle handle, String activityCode, RevisionMetadata meta,
                                ActivityVersionDto version2, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_BLOCKS);
        for (Config config : configList) {
            addNewBlock(activityId, config, activityCode, handle, meta, version2);
        }
    }

    private void addNewBlock(long activityId, Config config, String activityCode,
                             Handle handle, RevisionMetadata meta, ActivityVersionDto version2) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int order = config.getInt(ORDER);
        int sectionOrder = config.getInt(SECTION_ORDER);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(OSTEO_STUDY, activityCode).get();
        FormActivityDef currentDef = (FormActivityDef) handle.attach(ActivityDao.class).findDefByDtoAndVersion(activityDto, version2);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void addAdultNestedBlocks(long activityId, Handle handle, String activityCode,
                                      ActivityVersionDto version, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_NESTED_BLOCKS);
        for (Config config : configList) {
            addNewNestedBlock(activityId, config, handle, activityCode, version);
        }
    }

    private void addNewNestedBlock(long activityId, Config config,
                                   Handle handle, String activityCode, ActivityVersionDto version) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int sectionOrder = config.getInt(SECTION_ORDER);
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(OSTEO_STUDY, activityCode).get();
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
        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

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

    }
}


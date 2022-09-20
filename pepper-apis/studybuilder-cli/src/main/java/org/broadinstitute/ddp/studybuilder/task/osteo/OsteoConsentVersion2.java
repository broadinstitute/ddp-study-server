package org.broadinstitute.ddp.studybuilder.task.osteo;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiActivityValidation;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiBlockNesting;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.RevisionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterConstructorMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

@Slf4j
public class OsteoConsentVersion2 implements CustomTask {
    private static final String DATA_FILE = "patches/consent-version-2.conf";
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
    private static final String NESTING_ORDER = "nesting-orders";
    private static final String NESTING_ORDERING = "ordering";
    private static final String NESTING_SECTION_ORDER = "sectionOrder";
    private static final int DISPLAY_ORDER_GAP = 10;

    private Config dataCfg;
    private Config selfConsentDataCfg;
    private Config parentalConsentDataCfg;
    private Config consentAssentDataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private Gson gson;
    private long studyId;

    private JdbiActivity jdbiActivity;
    private JdbiActivityVersion jdbiVersion;

    private ActivityDao activityDao;

    private SqlHelper sqlHelper;

    private SectionBlockDao sectionBlockDao;

    private JdbiVariableSubstitution jdbiVarSubst;

    private JdbiRevision jdbiRevision;

    private JdbiActivityValidation jdbiActivityValidation;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file);
        this.cfgPath = cfgPath;

        this.varsCfg = varsCfg;

        if (!studyCfg.getString("study.guid").equals(OSTEO_STUDY)) {
            throw new DDPException("This task is only for the " + OSTEO_STUDY + " study!");
        }

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

        studyId = studyDto.getId();

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

        this.jdbiActivity = handle.attach(JdbiActivity.class);
        this.jdbiVersion = handle.attach(JdbiActivityVersion.class);
        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);
        this.jdbiActivityValidation = handle.attach(JdbiActivityValidation.class);

        String reasonParentalConsent = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCodeParentalConsent, versionTag);
        RevisionMetadata metaParentalConsent = new RevisionMetadata(ts, adminUser.getId(), reasonParentalConsent);

        ActivityVersionDto version2ForConsentAssent = getVersion2(handle, studyDto, metaConsentAssent, activityCodeConsentAssent);
        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCodeConsent);
        ActivityVersionDto version2ForParentalConsent = getVersion2(handle, studyDto, metaParentalConsent, activityCodeParentalConsent);

        updateVariables(handle, metaConsentAssent, version2ForConsentAssent);
        runAdultConsentUpdate(handle, metaConsent, studyDto, activityCodeConsent, version2ForConsent);
        runParentalConsentUpdate(handle, metaParentalConsent, studyDto, activityCodeParentalConsent, version2ForParentalConsent);
        runConsentAssentUpdate(handle, metaConsentAssent, studyDto, activityCodeConsentAssent, version2ForConsentAssent);

        updateIntro(handle, studyDto, metaConsentAssent, version2ForConsentAssent);

        long activityAssentConsentId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCodeConsentAssent);
        long activityPedConsentId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCodeParentalConsent);

        sqlHelper.updateActivityNameAndTitle(activityAssentConsentId, "Research Consent & Assent Form");
        sqlHelper.updateActivityNameAndTitle(activityPedConsentId, "Research Consent Form");
    }

    private void updateIntro(Handle handle, StudyDto studyDto, RevisionMetadata meta, ActivityVersionDto ver) {
        String activityCode = consentAssentDataCfg.getString("activityCode");

        String studyGuid = studyDto.getGuid();
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyGuidAndCode(studyGuid, activityCode)
                .orElseThrow(() -> new DDPException("Could not find id for activity " + activityCode + " and study id " + studyGuid));

        ActivityVersionDto currentActivityVerDto = activityDao.getJdbiActivityVersion().findByActivityCodeAndVersionTag(
                studyId, activityCode, "v1").get();

        ActivityDef currActivityDef = activityDao.findDefByDtoAndVersion(activityDto, currentActivityVerDto);
        FormActivityDef currFormActivityDef = (FormActivityDef) currActivityDef;

        long activityId = ActivityBuilder.findActivityId(handle, studyId, activityCode);

        long introBlockId = currFormActivityDef.getIntroduction().getBlocks()
                .get(0).getBlockId();
        sectionBlockDao.disableBlock(introBlockId, meta);

        FormBlockDef blockDefForAssent = GsonUtil.standardGson().fromJson(ConfigUtil
                .toJson(consentAssentDataCfg.getConfig("introductionForAssent")), FormBlockDef.class);


        sectionBlockDao.insertBlockForSection(activityId, ((FormActivityDef) currActivityDef).getSections().get(3)
                .getSectionId(), 0, blockDefForAssent, ver.getRevId());

        for (int i = 0; i < 3; i++) {
            FormBlockDef blockDef = GsonUtil.standardGson().fromJson(ConfigUtil
                    .toJson(consentAssentDataCfg.getConfig("introduction")), FormBlockDef.class);
            sectionBlockDao.insertBlockForSection(activityId, ((FormActivityDef) currActivityDef).getSections().get(i)
                    .getSectionId(), 0, blockDef, ver.getRevId());
        }
    }

    private FormActivityDef findActivityDef(String activityCode, String versionTag, StudyDto studyDto) {
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyIdAndCode(studyDto.getId(), activityCode)
                .orElseThrow(() -> new DDPException(
                        String.format("Couldn't find activity by studyId=%s and activityCode=%s",
                                studyDto.getId(), activityCode)));
        ActivityVersionDto versionDto = jdbiVersion
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, versionTag)
                .orElseThrow(() -> new DDPException(
                        String.format("Couldn't find activity version by studyId=%s, activityCode=%s and versionTag=%s",
                                studyDto.getId(), activityCode, versionTag)));
        return (FormActivityDef) activityDao
                .findDefByDtoAndVersion(activityDto, versionDto);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, "v2", meta);
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
        long tmplVarId = sqlHelper.findTemplateVariableIdByVariableName(varName);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);

        long newVarSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newVarSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
    }


    private void runConsentAssentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                        String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        // Replace existing activity validations. Removing old DOB validations here, adding there by OsteoDobValidations()
        jdbiActivityValidation._deleteValidationsByActivityId(activityId);
        updateEventExpressions(handle, activityId, consentAssentDataCfg.getString("newExpressionForWelcomeEvent"));
        updateAdultVariables(handle, meta, version2, consentAssentDataCfg);
        updateAdultTemplates(handle, meta, version2, consentAssentDataCfg);
        addAdultNestedBlocks(activityId, handle, meta, "CONSENT_ASSENT", version2, consentAssentDataCfg);
        addAdultBlocks(activityId, handle, "CONSENT_ASSENT", meta, version2, consentAssentDataCfg);
        reorderNestedBlock(handle, activityCode, version2, consentAssentDataCfg);
        detachQuestionFromBothSectionAndBlock(handle, "CONSENT_ASSENT_PARENT_SIGNATURE");
    }

    private void runParentalConsentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                          String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        // Replace existing activity validations. Removing old DOB validations here, adding there by OsteoDobValidations()
        jdbiActivityValidation._deleteValidationsByActivityId(activityId);
        updateEventExpressions(handle, activityId, parentalConsentDataCfg.getString("newExpressionForWelcomeEvent"));
        updateAdultVariables(handle, meta, version2, parentalConsentDataCfg);
        updateAdultTemplates(handle, meta, version2, parentalConsentDataCfg);
        addAdultNestedBlocks(activityId, handle, meta, "PARENTAL_CONSENT", version2, parentalConsentDataCfg);
        addAdultBlocks(activityId, handle, "PARENTAL_CONSENT", meta, version2, parentalConsentDataCfg);
        reorderNestedBlock(handle, activityCode, version2, parentalConsentDataCfg);
        detachQuestionFromBothSectionAndBlock(handle, "PARENTAL_CONSENT_SIGNATURE");
    }

    private void runAdultConsentUpdate(Handle handle, RevisionMetadata meta, StudyDto studyDto,
                                       String activityCode, ActivityVersionDto version2) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);

        // Replace existing activity validations. Removing old DOB validations here, adding there by OsteoDobValidations()
        jdbiActivityValidation._deleteValidationsByActivityId(activityId);
        updateEventExpressions(handle, activityId, selfConsentDataCfg.getString("newExpressionForWelcomeEvent"));
        updateAdultVariables(handle, meta, version2, selfConsentDataCfg);
        updateAdultTemplates(handle, meta, version2, selfConsentDataCfg);
        addAdultNestedBlocks(activityId, handle, meta, "CONSENT", version2, selfConsentDataCfg);
        addAdultBlocks(activityId, handle, "CONSENT", meta, version2, selfConsentDataCfg);
        reorderNestedBlock(handle, activityCode, version2, selfConsentDataCfg);
        detachQuestionFromBothSectionAndBlock(handle, "CONSENT_SIGNATURE");
    }

    private void updateEventExpressions(Handle handle, long activityId, String expression) {
        var helper = sqlHelper;
        var jdbiExpression = handle.attach(JdbiExpression.class);
        var eventId = helper.findEventAndItsCancelExprDto(EventTriggerType.USER_REGISTERED.toString(), activityId);
        log.info("Founded event configuration id {}", eventId);

        DBUtils.checkUpdate(1, helper.removeEventCancelExpr(eventId));
        jdbiExpression.deleteById(helper.findCancelExprId(eventId));
        log.info("Successfully removed cancelExprId for eventId {}", eventId);

        long exprId = jdbiExpression.insertExpression(expression).getId();
        log.info("Added expression to database with id {} and text {}", exprId, expression);

        DBUtils.checkUpdate(1, helper.updateEventPreExpr(eventId, exprId));
        log.info("Successfully added preconditionExprId {} for eventId {}: {}", exprId, eventId, expression);
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
        List<Long> templateVariableIdByVariableNames = sqlHelper.findTemplateVariableIdByVariableNames(varName);
        for (Long tmplVarId : templateVariableIdByVariableNames) {
            List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
            Translation currTranslation = transList.get(0);

            long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
            long[] revIds = {newFullNameSubRevId};
            jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
            jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, version2.getRevId(), tmplVarId);
        }
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
        BlockContentDto contentBlock = sqlHelper
                .findContentBlockByBodyText(versionDto.getActivityId(), templateSearchParam);

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
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyGuidAndCode(OSTEO_STUDY, activityCode).get();
        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, version2);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        RevisionDto revDto = RevisionDto.fromStartMetadata(version2.getRevId(), meta);
        sectionBlockDao.addBlock(activityId, currentSectionDef.getSectionId(),
                order, blockDef, revDto);
    }

    private void addAdultNestedBlocks(long activityId, Handle handle, RevisionMetadata meta, String activityCode,
                                      ActivityVersionDto version, Config dataCfg) {
        List<? extends Config> configList = dataCfg.getConfigList(NEW_NESTED_BLOCKS);
        for (Config config : configList) {
            addNewNestedBlock(activityId, config, meta, handle, activityCode, version);
        }
    }

    private void addNewNestedBlock(long activityId, Config config, RevisionMetadata meta,
                                   Handle handle, String activityCode, ActivityVersionDto version) {
        Config blockConfig = config.getConfig(BLOCK_KEY);
        int sectionOrder = config.getInt(SECTION_ORDER);
        int blockOrder = config.getInt("order");
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyGuidAndCode(OSTEO_STUDY, activityCode).get();
        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, version);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);
        FormBlockDef blockDef = gson.fromJson(ConfigUtil.toJson(blockConfig), FormBlockDef.class);

        Optional<FormBlockDef> blockOpt = currentSectionDef.getBlocks()
                .stream()
                .filter(formBlockDef -> formBlockDef.getBlockType() == BlockType.GROUP)
                .findFirst();

        blockOpt.ifPresent(formBlockDef -> insertNestedBlock(handle, activityId, formBlockDef.getBlockId(),
                blockOrder, blockDef, version.getRevId()));
    }

    void insertNestedBlock(Handle handle, long activityId, long parentBlockId, int position,
                           FormBlockDef nested, long revisionId) {
        JdbiBlockNesting jdbiBlockNesting = handle.attach(JdbiBlockNesting.class);

        if (nested.getBlockType().isContainerBlock()) {
            throw new IllegalStateException("Nesting container blocks is not allowed");
        }

        sectionBlockDao.insertBlockByType(activityId, nested, revisionId);
        jdbiBlockNesting.insert(parentBlockId, nested.getBlockId(), position, revisionId);
        log.info("Inserted nested block id {} for parent block id {}", nested.getBlockId(), parentBlockId);
    }

    private void reorderNestedBlock(Handle handle, String activityCode,
                                    ActivityVersionDto version2, Config dataCfg) {

        List<? extends Config> nestingConfigList = dataCfg.getConfigList(NESTING_ORDER);
        for (Config config : nestingConfigList) {
            List<String> configList = config.getStringList(NESTING_ORDERING);
            List<Long> blockOrdering = new ArrayList<>();
            for (String oldBlockTemplateText : configList) {
                String templateSearchParam = String.format("%s%s%s", "%", oldBlockTemplateText, "%");
                BlockContentDto contentBlock = sqlHelper
                        .findContentBlockByBodyText(version2.getActivityId(), templateSearchParam);
                blockOrdering.add(contentBlock.getBlockId());
            }

            int sectionOrder = config.getInt(NESTING_SECTION_ORDER);

            ActivityDto activityDto = jdbiActivity
                    .findActivityByStudyGuidAndCode(OSTEO_STUDY, activityCode).get();
            FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, version2);
            FormSectionDef currentSectionDef = currentDef.getSections().get(sectionOrder);

            Optional<FormBlockDef> blockOpt = currentSectionDef.getBlocks()
                    .stream()
                    .filter(formBlockDef -> formBlockDef.getBlockType() == BlockType.GROUP)
                    .findFirst();

            if (blockOpt.isPresent()) {
                long parentBlockId = blockOpt.get().getBlockId();
                int nestedBlockOrder = 0;
                for (Long nestedId : blockOrdering) {
                    nestedBlockOrder += DISPLAY_ORDER_GAP;
                    sqlHelper.updateNestingOrder(parentBlockId, nestedId, nestedBlockOrder);
                }
            }
        }
    }

    private void detachQuestionFromBothSectionAndBlock(Handle handle, String questionStableId) {
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Optional<QuestionDto> questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId);
        if (questionDto.isEmpty()) {
            throw new DDPException("Couldn't find question with stableId: " + questionStableId);
        }
        sqlHelper.detachQuestionFromBothSectionAndBlock(questionDto.get().getId());
        log.info("Question {} and its block were detached", questionStableId);
    }

    private interface SqlHelper extends SqlObject {

        default void detachQuestionFromBothSectionAndBlock(long questionId) {
            int numDeleted = _deleteSectionBlockMembershipByQuestionId(questionId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove question with questionId=" + questionId + " from section");
            }
            numDeleted = _deleteBlockQuestionByQuestionId(questionId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove question with questionId=" + questionId + " from block");
            }
        }

        @SqlUpdate("delete from form_section__block"
                + "  where block_id in (select block_id from block__question where question_id = :questionId)")
        int _deleteSectionBlockMembershipByQuestionId(@Bind("questionId") long questionId);

        @SqlUpdate("delete from block__question where question_id = :questionId")
        int _deleteBlockQuestionByQuestionId(@Bind("questionId") long questionId);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        long findTemplateVariableIdByVariableName(@Bind("variable_name") String variableName);

        @SqlQuery("select template_variable_id from template_variable where variable_name = :variable_name "
                + "order by template_variable_id desc")
        List<Long> findTemplateVariableIdByVariableNames(@Bind("variable_name") String variableName);

        @SqlUpdate("update block_nesting set display_order = :display_order"
                + " where parent_block_id = :parent_block_id and nested_block_id = :nested_block_id")
        void updateNestingOrder(@Bind("parent_block_id") long parentBlockId,
                                @Bind("nested_block_id") long nestedBlockId,
                                @Bind("display_order") int displayOrder);

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

        @SqlUpdate("update i18n_activity_detail set name = :name, title = :title where study_activity_id = :studyActivityId")
        int _updateActivityNameAndTitle(@Bind("studyActivityId") long studyActivityId,
                                        @Bind("name") String name,
                                        @Bind("title") String title);

        @SqlQuery("select en.event_configuration_id "
                + "from event_configuration en "
                + "    join event_action ea on en.event_action_id = ea.event_action_id "
                + "    join user_notification_event_action unea on ea.event_action_id = unea.user_notification_event_action_id "
                + "    join event_trigger et on en.event_trigger_id = et.event_trigger_id "
                + "    join event_trigger_type ett on et.event_trigger_type_id = ett.event_trigger_type_id "
                + "where "
                + "    ett.event_trigger_type_code = :triggerTypeCode "
                + "  and linked_activity_id = :activityId")
        long findEventAndItsCancelExprDto(@Bind("triggerTypeCode") String triggerTypeCode,
                                          @Bind("activityId") long activityId);

        @SqlQuery("select cancel_expression_id from event_configuration where event_configuration_id = :eventId")
        long findCancelExprId(@Bind("eventId") long eventId);

        @SqlUpdate("update event_configuration set precondition_expression_id = :exprId where event_configuration_id = :eventId")
        int updateEventPreExpr(@Bind("eventId") long eventId, @Bind("exprId") long exprId);

        @SqlUpdate("update event_configuration set cancel_expression_id = null where event_configuration_id = :eventId")
        int removeEventCancelExpr(@Bind("eventId") long eventId);

        default void updateActivityNameAndTitle(long studyActivityId, String name) {
            int numUpdated = _updateActivityNameAndTitle(studyActivityId, name, name);
            if (numUpdated != 1) {
                throw new DDPException("Expected to update 1 row for studyActivityId="
                        + studyActivityId + " but updated " + numUpdated);
            }
        }
    }
}


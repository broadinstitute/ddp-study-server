package org.broadinstitute.ddp.studybuilder.task.osteo;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityI18nDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dao.PdfDao;
import org.broadinstitute.ddp.db.dao.PdfSql;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.db.dto.UserDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ComponentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.PhysicianComponentDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.ActivityI18nDetail;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.model.activity.types.ComponentType;
import org.broadinstitute.ddp.model.pdf.PdfConfigInfo;
import org.broadinstitute.ddp.model.pdf.PdfConfiguration;
import org.broadinstitute.ddp.model.pdf.PdfTemplate;
import org.broadinstitute.ddp.model.pdf.PdfVersion;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.studybuilder.task.CustomTask;
import org.broadinstitute.ddp.studybuilder.task.UpdateTemplatesInPlace;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonPojoValidator;
import org.broadinstitute.ddp.util.GsonUtil;
import org.broadinstitute.ddp.util.JsonValidationError;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Task to make additional edits as part of the "Brain Tumor Project" rename.
 *
 * <p>This should be ran right after the BrainRename task. This assumes that activities will have a new version from
 * the BrainRename task, so it will make edits using that as the latest version.
 */
@Slf4j
public class OsteoMRFv2 implements CustomTask {
    private static final String ACTIVITY_DATA_FILE = "patches/mrf-v2.conf";
    private static final String V1_VERSION_TAG = "v1";

    private Config studyCfg;
    private Config activityDataCfg;
    private Gson gson;
    private GsonPojoValidator validator;

    private Handle handle;
    private ActivityDao activityDao;
    private ActivityI18nDao activityI18nDao;
    private JdbiActivity jdbiActivity;
    private JdbiActivityVersion jdbiVersion;
    private StudyDto studyDto;
    private UserDto adminUser;
    private SqlHelper helper;
    private SectionBlockDao sectionBlockDao;
    private PdfDao pdfDao;
    private PdfSql pdfSql;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {

        this.studyCfg = studyCfg;

        File file = cfgPath.getParent().resolve(ACTIVITY_DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.activityDataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.gson = GsonUtil.standardGson();
        this.validator = new GsonPojoValidator();
    }

    @Override
    public void run(Handle handle) {
        this.studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));
        this.adminUser = handle.attach(JdbiUser.class).findByUserGuid(studyCfg.getString("adminUser.guid"));

        this.handle = handle;
        this.activityDao = handle.attach(ActivityDao.class);
        this.activityI18nDao = handle.attach(ActivityI18nDao.class);
        this.jdbiActivity = handle.attach(JdbiActivity.class);
        this.jdbiVersion = handle.attach(JdbiActivityVersion.class);
        this.helper = handle.attach(SqlHelper.class);
        this.sectionBlockDao = handle.attach(SectionBlockDao.class);
        this.pdfDao = handle.attach(PdfDao.class);
        this.pdfSql = handle.attach(PdfSql.class);

        for (var activityCfg : activityDataCfg.getConfigList("updateActivities")) {
            updateActivity(activityCfg);
        }
    }

    private RevisionMetadata makeActivityRevMetadata(String activityCode, String newVersionTag) {
        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, newVersionTag);
        return RevisionMetadata.now(adminUser.getUserId(), reason);
    }

    private FormActivityDef findActivityDef(String activityCode, String versionTag) {
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

    private FormBlockDef parseFormBlockDef(Config sourceCfg) {
        FormBlockDef def = gson.fromJson(ConfigUtil.toJson(sourceCfg), FormBlockDef.class);
        List<JsonValidationError> errors = validator.validateAsJson(def);
        if (!errors.isEmpty()) {
            String msg = errors.stream()
                    .map(JsonValidationError::toDisplayMessage)
                    .collect(Collectors.joining(", "));
            if (def.getBlockType().equals(BlockType.QUESTION)) {
                throw new DDPException(String.format(
                        "Question definition with stableId=%s has validation errors: %s",
                        def.getQuestions().findFirst().get().getStableId(), msg));
            } else {
                throw new DDPException(String.format(
                        "%s block definition with has validation errors: %s",
                        def.getBlockType(), msg));
            }
        }
        return def;
    }

    private int getDisplayOrderOfLastBlock(FormSectionDef sectionDef) {
        return helper.findBlockDisplayOrder(
                sectionDef.getSectionId(),
                sectionDef.getBlocks().get(sectionDef.getBlocks().size() - 1).getBlockId());
    }

    private void updateActivity(Config activityCfg) {
        String activityCode = activityCfg.getString("activityCode");
        String newVersionTag = activityCfg.getString("newVersionTag");
        log.info("Editing activity {}...", activityCode);

        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        FormActivityDef activity = findActivityDef(activityCode, V1_VERSION_TAG);

        // Change activity version
        RevisionMetadata meta = makeActivityRevMetadata(activityCode, newVersionTag);

        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, newVersionTag, meta);
        log.info("Version {} is created with versionId={}, revisionId={}", newVersionTag, v2Dto.getId(), v2Dto.getRevId());

        ActivityVersionDto v1Dto = jdbiVersion.findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        log.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1Dto.getRevId());

        // update translatedNames / translatedTitles
        updateActivityDetails(activityId, activityCfg);

        // update writeOnce
        boolean writeOnce = activityCfg.getBoolean("newWriteOnce");
        DBUtils.checkUpdate(1, helper.updateActivityWriteOnce(activityId, writeOnce));
        log.info("Changed setting 'writeOnce' to {} for activity {}", writeOnce, activityCode);

        // update introduction
        var updateTemplates = new UpdateTemplatesInPlace();
        Config introSection = activityCfg.getConfig("introduction");
        updateTemplates.traverseSection(handle, 1, introSection, activity.getIntroduction(), meta.getTimestamp());

        // update agreement
        QuestionDef agreementQuestion = activity.getClosing().getBlocks().get(1).getQuestions().findFirst().get();
        updateTemplates.traverseQuestion(handle, activityCfg.getConfig("agreement_question"), agreementQuestion, meta.getTimestamp());

        // insert new question to closing
        long closeSectionId = activity.getClosing().getSectionId();
        int order = getDisplayOrderOfLastBlock(activity.getClosing());

        for (var blockCfg : activityCfg.getConfigList("closing_questions")) {
            order += SectionBlockDao.DISPLAY_ORDER_GAP;
            FormBlockDef blockDef = parseFormBlockDef(blockCfg);
            sectionBlockDao.insertBlockForSection(activityId, closeSectionId, order, blockDef, v2Dto.getRevId());
        }

        // disable all component blocks
        for (var section : activity.getSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.COMPONENT) {
                    if (((ComponentBlockDef) block).getComponentType() != ComponentType.PHYSICIAN) {
                        sectionBlockDao.disableBlock(block.getBlockId(), meta);
                        log.info("Disabled component blockId {}", block.getBlockId());
                    } else {
                        var templateDao = handle.attach(TemplateDao.class);
                        var physComponentRevId = ((PhysicianComponentDef) block).getComponentRevisionId();
                        var titleTemplateId = templateDao.insertTemplate(Template.text(""), physComponentRevId);
                        var subTitleTemplateId = templateDao.insertTemplate(Template.text(""), physComponentRevId);
                        var physComponentId = helper.findComponentIdByBlockId(block.getBlockId());
                        DBUtils.checkUpdate(1, helper.updatePhysicianTemplates(titleTemplateId, subTitleTemplateId, physComponentId));
                        log.info("Successfully updated title and subtitle templates for Physician Component id {}", physComponentId);
                    }
                }
            }
        }

        // update study-pdfs.conf
        for (var pdfConfigName : activityCfg.getStringList("pdfs")) {
            PdfConfigInfo info = pdfDao.findConfigInfoByStudyIdAndName(studyDto.getId(), pdfConfigName)
                    .orElseThrow(() -> new DDPException("Could not find consent pdf info"));

            List<PdfVersion> versions = pdfDao.findOrderedConfigVersionsByConfigId(info.getId());
            if (versions.size() != 1) {
                throw new DDPException("Expected one consent pdf version but found " + versions.size());
            }

            PdfVersion terminatedVersion = versions.get(0);
            pdfSql.updateConfigVersion(terminatedVersion.getId(), v1Dto.getRevId());
            log.info("Terminated {} of pdf configuration with name={}, filename={}, displayName={}, versionId={}",
                    terminatedVersion.getVersionTag(), info.getConfigName(), info.getFilename(),
                    info.getDisplayName(), terminatedVersion.getId());

            PdfVersion newVersion = new PdfVersion(
                    terminatedVersion.getConfigId(),
                    newVersionTag, v2Dto.getRevId(),
                    terminatedVersion.getDataSources());

            PdfConfigInfo newInfo = new PdfConfigInfo(
                    studyDto.getId(),
                    info.getConfigName(),
                    info.getFilename(),
                    info.getDisplayName());

            PdfConfiguration pdfV2 = new PdfConfiguration(newInfo, newVersion);
            List<PdfTemplate> templates = pdfDao.findBaseTemplatesByVersionId(terminatedVersion.getId());
            long versionId = pdfDao.insertNewConfigVersion(pdfV2, templates);
            log.info("Added pdf configuration version for id={} with name={}, filename={}, "
                            + "displayName={}, versionId={}, versionTag={}",
                    pdfV2.getId(), pdfV2.getConfigName(), pdfV2.getFilename(),
                    pdfV2.getDisplayName(), versionId, pdfV2.getVersion().getVersionTag());
        }
    }

    private void updateActivityDetails(long activityId, Config activityCfg) {
        ActivityI18nDetail i18nDetail = activityI18nDao
                .findDetailsByActivityIdAndTimestamp(activityId, Instant.now().toEpochMilli())
                .iterator().next();
        var newI18nDetail = new ActivityI18nDetail(
                i18nDetail.getId(),
                i18nDetail.getActivityId(),
                i18nDetail.getLangCodeId(),
                i18nDetail.getIsoLangCode(),
                activityCfg.getConfigList("translatedNames").get(0).getString("text"),
                i18nDetail.getSecondName(),
                activityCfg.getConfigList("translatedTitles").get(0).getString("text"),
                i18nDetail.getSubtitle(),
                i18nDetail.getDescription(),
                i18nDetail.getRevisionId());
        activityI18nDao.updateDetails(List.of(newI18nDetail));
        log.info("Updated Activity I18n Detail for activity {}", activityCfg.getString("activityCode"));
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select display_order from form_section__block where form_section_id = :sectionId and block_id = :blockId ")
        int findBlockDisplayOrder(@Bind("sectionId") long sectionId, @Bind("blockId") long blockId);

        @SqlUpdate("update study_activity set is_write_once = :writeOnce where study_activity_id = :activityId")
        int updateActivityWriteOnce(@Bind("activityId") long activityId, @Bind("writeOnce") boolean writeOnce);

        @SqlUpdate("update user_announcement_event_action set message_template_id = :msgTemplateId where event_action_id ="
                + "(select event_action_id from event_configuration where event_configuration_id = :eventConfigurationId)")
        int updateAnnouncementTemplateId(@Bind("msgTemplateId") long msgTemplateId,
                                         @Bind("eventConfigurationId") long eventConfigurationId);

        @SqlQuery("select ec.event_configuration_id from event_configuration ec "
                + "join activity_instance_creation_action aica on aica.activity_instance_creation_action_id = ec.event_action_id "
                + "where aica.study_activity_id = :activityId")
        List<Long> findEventConfigurationIdByActivityId(@Bind("activityId") long activityId);

        @SqlUpdate("update event_configuration set precondition_expression_id = :exprId, execution_order = :order "
                + "where event_configuration_id = :eventConfigurationId")
        int updateEventExpressionAndOrder(@Bind("exprId") long exprId,
                                          @Bind("order") int order,
                                          @Bind("eventConfigurationId") long eventConfigurationId);

        @SqlUpdate("update institution_physician_component set title_template_id = :titleTemplateId, "
                + "        subtitle_template_id = :subtitleTemplateId where institution_physician_component_id = :componentId")
        int updatePhysicianTemplates(
                @Bind("titleTemplateId") Long titleTemplateId,
                @Bind("subtitleTemplateId") Long subtitleTemplateId,
                @Bind("componentId") long componentId);

        @SqlQuery("select ipc.institution_physician_component_id from institution_physician_component ipc "
                + "join component c on ipc.institution_physician_component_id = c.component_id "
                + "join block_component bc on c.component_id = bc.component_id "
                + "join block b on bc.block_id = b.block_id "
                + "where b.block_id = :blockId; ")
        int findComponentIdByBlockId(@Bind("blockId") long blockId);
    }
}

package org.broadinstitute.ddp.studybuilder.task.lms;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiBlockContent;
import org.broadinstitute.ddp.db.dao.JdbiFormSectionBlock;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.TemplateDao;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.BlockContentDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.SectionBlockMembershipDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
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

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class LmsGermlinePedConsentVersion2 implements CustomTask {

    private static final String DATA_FILE = "patches/germline-consent-addendum-ped-v2.conf";
    private static final String STUDY = "cmi-lms";
    private static final String VARIABLES_UPD = "variables-update";
    private static final String BLOCK_KEY = "blockNew";
    private static final String BLOCK_UPDATES = "block-updates";
    private static final String BLOCK_ADD = "block-add";
    private static final String OLD_TEMPLATE_KEY = "old_template_search_text";
    private static final String DATE_QUESTION_STABLEID = "ADDENDUM_CONSENT_PATIENT_DOB_PEDIATRIC";
    private static final Gson gson = GsonUtil.standardGson();

    private Config dataCfg;
    private Config varsCfg;
    private Path cfgPath;
    private Instant timestamp;
    private String versionTag;
    private Config cfg;
    private ActivityDao activityDao;
    private SqlHelper sqlHelper;
    private JdbiVariableSubstitution jdbiVarSubst;
    private JdbiRevision jdbiRevision;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);

        this.cfgPath = cfgPath;
        this.varsCfg = varsCfg;

        String studyGuid = studyCfg.getString("study.guid");
        if (!studyGuid.equalsIgnoreCase(STUDY)) {
            throw new DDPException("This task is only for the " + STUDY + " study!");
        }

        cfg = studyCfg;
        versionTag = dataCfg.getString("versionTag");
        timestamp = Instant.now();
    }

    @Override
    public void run(Handle handle) {
        User adminUser = handle.attach(UserDao.class).findUserByGuid(cfg.getString("adminUser.guid")).get();

        String activityCode = "GERMLINE_CONSENT_ADDENDUM_PEDIATRIC";
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(dataCfg.getString("study.guid"));

        log.info("Changing version of {} to {} with timestamp={}", activityCode, versionTag, timestamp);
        long ts = this.timestamp.toEpochMilli();

        String reason = String.format(
                "Update activity with studyGuid=%s activityCode=%s to versionTag=%s",
                studyDto.getGuid(), activityCode, versionTag);
        RevisionMetadata metaConsent = new RevisionMetadata(ts, adminUser.getId(), reason);

        this.activityDao = handle.attach(ActivityDao.class);
        this.sqlHelper = handle.attach(SqlHelper.class);
        this.jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        this.jdbiRevision = handle.attach(JdbiRevision.class);

        log.info("Revisioning: {} ", activityCode);
        ActivityVersionDto version2ForConsent = getVersion2(handle, studyDto, metaConsent, activityCode);

        runGermlinePedConsentUpdate(handle, metaConsent, version2ForConsent, dataCfg);

        //disable Child Date Question & add new Content block with sys date
        disableQuestion(handle, studyDto.getId(), metaConsent, version2ForConsent);
    }

    private ActivityVersionDto getVersion2(Handle handle, StudyDto studyDto, RevisionMetadata meta, String activityCode) {
        long activityId = ActivityBuilder.findActivityId(handle, studyDto.getId(), activityCode);
        return activityDao.changeVersion(activityId, "v2", meta);
    }

    private void runGermlinePedConsentUpdate(Handle handle, RevisionMetadata meta, ActivityVersionDto version2, Config dataCfg) {
        updateTemplates(handle, meta, version2, dataCfg); //revision full Template
        updateTemplateVariables(meta, version2, dataCfg); //revision only variables in the Template

    }

    private void updateTemplates(Handle handle, RevisionMetadata meta, ActivityVersionDto version2, Config dataCfg) {
        log.info("UPDATE Templates/Content Blocks");
        List<? extends Config> configList = dataCfg.getConfigList(BLOCK_UPDATES);
        for (Config config : configList) {
            revisionContentBlockTemplate(handle, meta, version2, config);
        }
    }

    private void updateTemplateVariables(RevisionMetadata meta,
                                         ActivityVersionDto version2, Config dataCfg) {
        log.info("UPDATE Template variables");
        List<? extends Config> configList = dataCfg.getConfigList(VARIABLES_UPD);
        for (Config config : configList) {
            TemplateVariable templateVariable = gson.fromJson(ConfigUtil.toJson(config), TemplateVariable.class);
            revisionVariableTranslation(templateVariable, meta, version2);
        }
    }

    private void revisionVariableTranslation(TemplateVariable templateVariable,
                                             RevisionMetadata meta, ActivityVersionDto version2) {
        log.info("revisioning and updating template variable: {}", templateVariable.getName());
        Long templateVariableId = sqlHelper.findVariableIdByNameAndActivityId(templateVariable.getName(), version2.getActivityId());
        log.info("Tmpl variableId  {} ", templateVariableId);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(templateVariableId);
        //all translations have same revision.. hence using first.. lms has only en any way.
        long revId = jdbiRevision.copyAndTerminate(transList.get(0).getRevisionId().get(), meta);
        long[] revIds = new long[transList.size()];
        Arrays.fill(revIds, revId);
        List<Long> substitutionIds = new ArrayList<>();
        log.info("subs/translations count: {} for varId : {} ", transList.size(), templateVariableId);
        for (var currTranslation : transList) {
            String language = currTranslation.getLanguageCode();
            String currentText = currTranslation.getText();

            String newTxt = templateVariable.getTranslation(language).get().getText();
            log.info("terminated: language: {} revId: {} new Text: {} currText: {}", language, currTranslation.getRevisionId(),
                    newTxt, currentText);

            substitutionIds.add(currTranslation.getId().get());
            jdbiVarSubst.insert(currTranslation.getLanguageCode(), templateVariable.getTranslation(language).get().getText(),
                    version2.getRevId(), templateVariableId);
        }
        int[] ids = jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(substitutionIds, revIds);
        if (ids.length != revIds.length) {
            throw new DDPException("returned ids length " + ids.length + "  doesnt match revIds passed length " + revIds.length);
        }
        log.info("revisioned and updated template variable: {}", templateVariableId);
    }

    private void revisionContentBlockTemplate(Handle handle, RevisionMetadata meta, ActivityVersionDto versionDto, Config conf) {
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

    private void addContentBlock(Handle handle, ActivityVersionDto versionDto, Config conf,
                                 long sectionId, int displayOrder) {

        ContentBlockDef contentBlockDef = gson.fromJson(ConfigUtil.toJson(conf), ContentBlockDef.class);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        sectionBlockDao.insertBlockForSection(versionDto.getActivityId(), sectionId,
                displayOrder, contentBlockDef, versionDto.getRevId());
        log.info("Created block_content");
    }

    private void disableQuestion(Handle handle, Long studyId, RevisionMetadata meta, ActivityVersionDto versionDto) {
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        JdbiFormSectionBlock jdbiFormSectionBlock = handle.attach(JdbiFormSectionBlock.class);
        QuestionDto dateDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, DATE_QUESTION_STABLEID).get();
        long childDateBlockId = this.sqlHelper.findQuestionBlockId(dateDto.getId());
        SectionBlockMembershipDto dateSectionDto = jdbiFormSectionBlock.getActiveMembershipByBlockId(childDateBlockId).get();
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        sectionBlockDao.disableBlock(childDateBlockId, meta);
        log.info("Disabled Question: {} . blockId: {} ", DATE_QUESTION_STABLEID, childDateBlockId);
        dateSectionDto.getDisplayOrder();
        long sectionId = dateSectionDto.getSectionId();
        addContentBlock(handle, versionDto, dataCfg.getConfig(BLOCK_ADD), sectionId, dateSectionDto.getDisplayOrder() + 10);
        log.info("Added new sysdate content block");
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


        @SqlQuery("select tv.template_variable_id from block_content as bt"
                + "  join template as tmpl on tmpl.template_id = bt.body_template_id"
                + "  join template_variable tv on tv.template_id = tmpl.template_id "
                + " where tv.variable_name = :variableName"
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
        Long findVariableIdByNameAndActivityId(@Bind("variableName") String variableName, @Bind("activityId") Long activityId);

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

    }

}

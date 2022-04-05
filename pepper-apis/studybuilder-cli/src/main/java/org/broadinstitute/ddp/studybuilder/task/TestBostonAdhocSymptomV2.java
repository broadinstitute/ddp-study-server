package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import lombok.extern.slf4j.Slf4j;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.JdbiVariableSubstitution;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiBlockExpression;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One-off task to add adhoc symptom message to TestBoston in deployed environments.
 */
@Slf4j
public class TestBostonAdhocSymptomV2 implements CustomTask {
    private static final String ADHOC_SYMPTOM_V2_FILE = "adhoc-symptom-v2.conf";
    private static final String STUDY_GUID = "testboston";
    private static final String V1_VERSION_TAG = "v1";

    private Path cfgPath;
    private Config studyCfg;
    private Config varsCfg;
    private Gson gson;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.cfgPath = cfgPath;
        this.studyCfg = studyCfg;
        this.varsCfg = varsCfg;
        this.gson = GsonUtil.standardGson();
    }

    @Override
    public void run(Handle handle) {
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(STUDY_GUID);
        User adminUser = handle.attach(UserDao.class).findUserByGuid(studyCfg.getString("adminUser.guid")).get();
        var activityBuilder = new ActivityBuilder(cfgPath.getParent(), studyCfg, varsCfg, studyDto, adminUser.getId());

        var activityDao = handle.attach(ActivityDao.class);
        var sectionBlockDao = handle.attach(SectionBlockDao.class);
        var jdbiVariableSubstitution = handle.attach(JdbiVariableSubstitution.class);
        var jdbiExpression = handle.attach(JdbiExpression.class);
        var jdbiBlockExpression = handle.attach(JdbiBlockExpression.class);
        var helper = handle.attach(SqlHelper.class);

        //
        // Load v2 definition.
        //

        Config v2Cfg = activityBuilder.readDefinitionConfig(ADHOC_SYMPTOM_V2_FILE);
        var v2Def = (FormActivityDef) gson.fromJson(ConfigUtil.toJson(v2Cfg), ActivityDef.class);
        activityBuilder.validateDefinition(v2Def);
        log.info("Loaded activity definition from file: {}", ADHOC_SYMPTOM_V2_FILE);

        //
        // Create version 2.
        //

        String activityCode = v2Cfg.getString("activityCode");
        String v2VersionTag = v2Cfg.getString("versionTag");
        log.info("Creating version {} of {}...", v2VersionTag, activityCode);

        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, activityCode).get();
        long activityId = activityDto.getActivityId();

        String reason = String.format("Revision activity with studyGuid=%s activityCode=%s versionTag=%s",
                studyDto.getGuid(), activityCode, v2VersionTag);
        var metadata = RevisionMetadata.now(adminUser.getId(), reason);
        ActivityVersionDto v2Dto = activityDao.changeVersion(activityId, v2VersionTag, metadata);
        long revisionId = v2Dto.getRevId();
        log.info("Version {} is created with versionId={}, revisionId={}", v2VersionTag, v2Dto.getId(), revisionId);

        ActivityVersionDto v1Dto = handle.attach(JdbiActivityVersion.class)
                .findByActivityCodeAndVersionTag(studyDto.getId(), activityCode, V1_VERSION_TAG)
                .orElseThrow(() -> new DDPException("Could not find version " + V1_VERSION_TAG));
        long v1TerminatedRevId = v1Dto.getRevId(); // v1 should be terminated already after adding v2 above.
        log.info("Version {} is terminated with revisionId={}", V1_VERSION_TAG, v1TerminatedRevId);

        //
        // Add new question blocks to v2.
        //

        log.info("Starting inserting new questions blocks...");
        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, v2Dto);
        FormSectionDef currentSectionDef = currentDef.getSections().get(0);
        ConditionalBlockDef question2Def = (ConditionalBlockDef) currentSectionDef.getBlocks().get(1);

        List<FormBlockDef> blocks = v2Def.getSections().get(0).getBlocks();

        // ADHOC_HAVE_SYMPTOMS question

        var block = blocks.get(1);
        var stableId = block.getQuestions().findFirst().get().getStableId();
        int displayOrder = helper.findBlockDisplayOrder(currentSectionDef.getSectionId(),
                currentSectionDef.getBlocks().get(0).getBlockId()) + 1;

        sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(), displayOrder, block, revisionId);
        log.info("Inserted new {} block with id={}, stableId={}, displayOrder={} for activityCode={}, sectionId={}",
                block.getBlockType(), block.getBlockId(), stableId, displayOrder, activityCode, currentSectionDef.getSectionId());

        // Conditional question, BOOSTER_VACCINE, ADHOC_SYMPTOM_DATE_OF_VACCINATION, ADHOC_SYMPTOMS_MANUFACTURER

        block = blocks.get(4);
        stableId = block.getQuestions().findFirst().get().getStableId();
        displayOrder = helper.findBlockDisplayOrder(currentSectionDef.getSectionId(),
                currentSectionDef.getBlocks().get(2).getBlockId()) + SectionBlockDao.DISPLAY_ORDER_GAP;

        sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(), displayOrder, block, revisionId);
        log.info("Inserted new {} block with id={}, stableId={}, displayOrder={} for activityCode={}, sectionId={}",
                block.getBlockType(), block.getBlockId(), stableId, displayOrder, activityCode, currentSectionDef.getSectionId());

        //
        // Update template for: content block, second question
        //

        log.info("Starting updating templates in content and second question...");

        ContentBlockDef contentBlock = (ContentBlockDef) blocks.get(0);
        TemplateVariable templateVarContent = contentBlock.getBodyTemplate().getVariables().stream().findFirst().get();
        String templateVarContentName = templateVarContent.getName();

        ConditionalBlockDef conditionalBlock = (ConditionalBlockDef) blocks.get(2);
        QuestionDef questionDef2 = conditionalBlock.getControl();
        TemplateVariable templateVarQuestion = questionDef2.getPromptTemplate().getVariables().stream().findFirst().get();
        String templateVarQuestionName = templateVarQuestion.getName();

        long[] revIds = {v1TerminatedRevId};
        List<Long> subsContentVars = ((ContentBlockDef) currentSectionDef.getBlocks().get(0)).getBodyTemplate().getVariables()
                .stream()
                .findFirst()
                .get()
                .getTranslations().stream()
                .map(t -> t.getId().get())
                .collect(Collectors.toList());
        List<Long> subsQuestionVars = question2Def.getControl().getPromptTemplate().getVariables()
                .stream()
                .findFirst()
                .get()
                .getTranslations().stream()
                .map(t -> t.getId().get())
                .collect(Collectors.toList());

        var updatedAr = jdbiVariableSubstitution.bulkUpdateRevisionIdsBySubIds(subsContentVars, revIds);
        var updated = Arrays.stream(updatedAr).count();

        if (updated != 1) {
            throw new DDPException("Wrong amount of updates v1 in content block subs template revisions, must be "
                    + ", but updated " + updated + ", "
                    + Arrays.stream(updatedAr).mapToObj(String::valueOf).collect(Collectors.toList()));
        }

        log.info("Update revisionId={} for template substitution ids={}",
                v1TerminatedRevId, subsContentVars);

        updatedAr = jdbiVariableSubstitution.bulkUpdateRevisionIdsBySubIds(subsQuestionVars, revIds);
        updated = Arrays.stream(updatedAr).count();
        if (updated != 1) {
            throw new DDPException("Wrong amount of updates v1 in second question block subs template revisions, must be 1"
                    + ", but updated " + updated + ", "
                    + Arrays.stream(updatedAr).mapToObj(String::valueOf).collect(Collectors.toList()));
        }
        log.info("Update revisionId={} for template substitution ids={}",
                v1TerminatedRevId, subsQuestionVars);

        List<Translation> translations = templateVarContent.getTranslations();
        log.info("Select {} language codes = {}",
                translations.size(), translations.stream().map(Translation::getLanguageCode).collect(Collectors.toList()));

        for (Translation translation : translations) {

            String isoCode = translation.getLanguageCode();

            long templateVarIdContent = helper. findTemplateVariableId(templateVarContentName, v1TerminatedRevId);
            String translatedTextContent = templateVarContent.getTranslation(isoCode).get().getText();
            jdbiVariableSubstitution.insert(isoCode, translatedTextContent, revisionId, templateVarIdContent);
            log.info("Insert substitution for '{}' template variable and '{}' language code, revision = {}",
                    templateVarContentName, isoCode, revisionId);

            long templateVarIdQuestion = helper.findTemplateVariableId(templateVarQuestionName, v1TerminatedRevId);
            String translatedTextQuestion = templateVarQuestion.getTranslation(isoCode).get().getText();
            jdbiVariableSubstitution.insert(isoCode, translatedTextQuestion, revisionId, templateVarIdQuestion);
            log.info("Insert substitution for '{}' template variable and '{}' language code, revision = {}",
                    templateVarQuestionName, isoCode, revisionId);
        }

        //
        // Update expressions for second question
        //

        log.info("Starting updating expressions for second question...");

        String blockExpressionText = conditionalBlock.getShownExpr();
        String expressionText2 = conditionalBlock.getNested().get(0).getShownExpr();

        long blockId = question2Def.getBlockId();
        long nestedBlockId = question2Def.getNested().get(0).getBlockId();

        String blockV1Expression = jdbiExpression.generateUniqueGuid();
        var blockV1ExpressionId = jdbiExpression.insert(blockV1Expression, "true");
        jdbiBlockExpression.insert(blockId, blockV1ExpressionId, v1TerminatedRevId);
        log.info("Insert expression for v1 with id={} for block={}, revisionId={}",
                blockV1ExpressionId, blockId, v1TerminatedRevId);

        var nestedV1ExpressionBlockId = jdbiBlockExpression.getActiveByBlockId(nestedBlockId).get().getId();
        updated = jdbiBlockExpression.updateRevisionIdById(nestedV1ExpressionBlockId, v1TerminatedRevId);
        if (updated != 1) {
            throw new DDPException("Wrong amount of updates v1 revisions for nested block in second question, must be 1"
                    + ", but updated " + updated);
        }
        log.info("Update expression block id={} for v1 revisionId={} for nested block id={}",
                nestedV1ExpressionBlockId, v1TerminatedRevId, nestedBlockId);

        String blockV2Expression = jdbiExpression.generateUniqueGuid();
        var blockV2ExpressionId = jdbiExpression.insert(blockV2Expression, blockExpressionText);
        jdbiBlockExpression.insert(blockId, blockV2ExpressionId, revisionId);
        log.info("Insert expression with id={} for block={}, revisionId={}",
                blockV2ExpressionId, blockId, revisionId);

        String nestedExpression = jdbiExpression.generateUniqueGuid();
        var nestedExpressionId = jdbiExpression.insert(nestedExpression, expressionText2);
        jdbiBlockExpression.insert(nestedBlockId, nestedExpressionId, revisionId);
        log.info("Insert expression with id={} for nested block={}, revisionId={}",
                nestedExpressionId, nestedBlockId, revisionId);

        //
        // hideNumber = false (ADHOC_SYMPTOMS)
        //

        updated = helper.updateHideNumber(question2Def.getControl().getQuestionId());
        if (updated != 1) {
            throw new DDPException("Wrong amount of update hide number for second question, must be 1"
                    + ", but updated " + updated);
        }
        log.info("Hide question number for question={}", question2Def.getControl().getQuestionId());
    }

    private interface SqlHelper extends SqlObject {
        @SqlQuery("select tv.template_variable_id "
                + "from template_variable as tv "
                + "join i18n_template_substitution as ts on tv.template_variable_id = ts.template_variable_id "
                + "where tv.variable_name = :value and ts.revision_id = :revision")
        long findTemplateVariableId(@Bind("value") String templateVar, @Bind("revision") long revisionId);

        @SqlQuery("select display_order from form_section__block where form_section_id = :sectionId and block_id = :blockId ")
        int findBlockDisplayOrder(@Bind("sectionId") long sectionId, @Bind("blockId") long blockId);

        @SqlUpdate("update question set hide_number=1 where question_id = :questionId")
        int updateHideNumber(@Bind("questionId") long questionId);
    }
}

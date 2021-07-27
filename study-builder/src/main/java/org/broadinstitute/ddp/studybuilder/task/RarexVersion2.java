package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.types.BlockType;
import org.broadinstitute.ddp.studybuilder.ActivityBuilder;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

public class RarexVersion2 implements CustomTask {

    private static final Logger LOG = LoggerFactory.getLogger(RarexVersion2.class);
    private static final String STUDY_GUID = "rarex";
    private static final String DATA_FILE = "patches/patch_0721.conf";

    private Config studyCfg;
    private Gson gson;
    private Config dataCfg;
    private long studyId;

    private JdbiActivityVersion jdbiVersion;
    private ActivityDao activityDao;
    private JdbiActivity jdbiActivity;

    @Override
    public void init(Path cfgPath, Config studyCfg, Config varsCfg) {
        if (!studyCfg.getString("study.guid").equals(STUDY_GUID)) {
            throw new DDPException("This task is only for the " + STUDY_GUID + " study!");
        }
        this.studyCfg = studyCfg;
        gson = GsonUtil.standardGson();
        File file = cfgPath.getParent().resolve(DATA_FILE).toFile();
        if (!file.exists()) {
            throw new DDPException("Data file is missing: " + file);
        }
        this.dataCfg = ConfigFactory.parseFile(file).resolveWith(varsCfg);
    }

    @Override
    public void run(Handle handle) {
        LanguageStore.init(handle);
        StudyDto studyDto = handle.attach(JdbiUmbrellaStudy.class).findByStudyGuid(studyCfg.getString("study.guid"));

        jdbiVersion = handle.attach(JdbiActivityVersion.class);
        jdbiActivity = handle.attach(JdbiActivity.class);
        activityDao = handle.attach(ActivityDao.class);

        studyId = studyDto.getId();
        SqlHelper helper = handle.attach(SqlHelper.class);

        long generalInformationId = ActivityBuilder.findActivityId(handle, studyId, "GENERAL_INFORMATION");
        long healthAndDevelopmentId = ActivityBuilder.findActivityId(handle, studyId, "HEALTH_AND_DEVELOPMENT");
        long prequalId = ActivityBuilder.findActivityId(handle, studyId, "PREQUAL");
        long addParticipantId = ActivityBuilder.findActivityId(handle, studyId, "ADD_PARTICIPANT");
        long legacyId = ActivityBuilder.findActivityId(handle, studyId, "LEGACY");
        long qolSelfId = ActivityBuilder.findActivityId(handle, studyId, "QUALITY_OF_LIFE");
        long qolChildId = ActivityBuilder.findActivityId(handle, studyId, "CHILD_QUALITY_OF_LIFE");
        long qolPatientId = ActivityBuilder.findActivityId(handle, studyId, "PATIENT_QUALITY_OF_LIFE");

        // REFID 27
        replaceContentBlockVariableText(helper, generalInformationId,
                "general_information_health_insurance_coverage_exp",
                "This is just to look at insurance coverage in rare disease in general, NOT to track insurance details.");

        // REFID 71
        replaceContentBlockVariableText(helper, healthAndDevelopmentId,
                "h_d_teeth_issues_exp",
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function. You may have seen a dentist for these issues.");
        replaceContentBlockVariableText(helper, healthAndDevelopmentId,
                "h_d_patient_teeth_issues_exp",
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function. The patient may have "
                + "seen a dentist for these issues.");

        // REFID 83
        replaceQuestionBlockVariableText(helper, prequalId, "SELF_COUNTRY",
                "COUNTRY_picklist_label",
                "Choose Country or Territory...");
        replaceQuestionBlockVariableText(helper, addParticipantId, "PARTICIPANT_COUNTRY",
                "COUNTRY_picklist_label",
                "Choose Country or Territory...");

        // REFID 98
        List<Long> varIds = helper.findVariableIdsByText(healthAndDevelopmentId,
                "Have you ever been diagnosed with cancer, colon polyps or a non-cancerous tumor?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "Have you ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId,
                "Has the patient ever been diagnosed with cancer, colon polyps or a non-cancerous tumor? ");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "Has the patient ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId,
                "Have you had issues with your teeth or mouth?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "Have you had issues with your TEETH or MOUTH?"));
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId,
                "Has the patient had issues with their teeth or mouth?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "Has the patient had issues with their TEETH or MOUTH?"));

        // REFID 105
        varIds = helper.findVariableIdsByText(healthAndDevelopmentId,
                "For the conditions you listed above do you have reports or summaries to upload as support?");
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "Do you have genetic reports or summaries to upload?"));

        // REFID 99
        varIds = helper.findPicklistOptionVariableIdsByStableIds(addParticipantId, "DYRK1",
                "PRIMARY_DIAGNOSIS");
        varIds.addAll(helper.findPicklistOptionVariableIdsByStableIds(legacyId, "DYRK1",
                "LEGACY_PRIMARY_DIAGNOSIS"));
        varIds.forEach(varId -> helper.updateVarValueByTemplateVarId(varId,
                "DYRK1A - dual-specificity tyrosine phosphorylation regulated kinase 1A"));

        // REFID 101
        Config option = dataCfg.getConfig("stateDC");
        addPicklistOption(handle, studyId, Set.of("PARTICIPANT_STATE", "SELF_STATE"), option, 75);

        // REFID 107
        option = dataCfg.getConfig("preferNotToAnswer");
        Config ruleConfig = dataCfg.getConfig("qolRequiredValidation");
        Set<String> stableIds = Set.of("QOL_SELF_WALK", "QOL_SELF_ERRANDS", "QOL_SELF_FEARFUL", "QOL_SELF_FOCUS",
                "QOL_SELF_OVERWHELMED", "QOL_SELF_UNEASY", "QOL_SELF_WORTHLESS", "QOL_SELF_HELPLESS", "QOL_SELF_DEPRESSED",
                "QOL_SELF_HOPELESS", "QOL_SELF_FEEL_FATIGUED", "QOL_SELF_TROUBLE_STARTING", "QOL_SELF_RUN_DOWN",
                "QOL_SELF_FATIGUED_AVERAGE", "QOL_SELF_SLEEP_QUALITY", "QOL_SELF_SLEEP_REFRESHING", "QOL_SELF_SLEEP_PROBLEM",
                "QOL_SELF_SLEEP_DIFFICULTY", "QOL_SELF_TROUBLE_LEISURE_ACTV", "QOL_SELF_TROUBLE_FAMILY_ACTV",
                "QOL_SELF_TROUBLE_DOING_WORK", "QOL_SELF_TROUBLE_FRIENDS_ACTV", "QOL_SELF_PAIN_DAILY_ACTV",
                "QOL_SELF_PAIN_WORK", "QOL_SELF_PAIN_SOCIAL", "QOL_SELF_PAIN_CHORES", "QOL_SELF_STAIRS", "QOL_SELF_CHORES",

                "QOL_CHILD_SPORT_EXERCISE", "QOL_CHILD_GET_UP_FROM_FLOOR", "QOL_CHILD_WALK_UP_STAIRS",
                "QOL_CHILD_ABLE_TO_DO_ACTIVITIES", "QOL_CHILD_AWFUL_MIGHT_HAPPEN", "QOL_CHILD_FELT_NERVOUS",
                "QOL_CHILD_FELT_WORRIED", "QOL_CHILD_FELT_WORRIED_AT_HOME", "QOL_CHILD_FELT_EVRTHNG_WENT_WRONG",
                "QOL_CHILD_FELT_LONELY", "QOL_CHILD_FELT_SAD", "QOL_CHILD_HARD_TO_HAVE_FUN", "QOL_CHILD_TIRED_TO_KEEP_UP",
                "QOL_CHILD_GET_TIRED_EASILY", "QOL_CHILD_TOO_TIRED_FOR_SPORT", "QOL_CHILD_TOO_TIRED_TO_ENJOY",
                "QOL_CHILD_FELT_ACCEPTED", "QOL_CHILD_COUNT_ON_FRIENDS", "QOL_CHILD_FRIENDS_HELP",
                "QOL_CHILD_WANT_TO_BE_FRIENDS", "QOL_CHILD_TROUBLE_SLEEPING_PAIN", "QOL_CHILD_PAY_ATTENTION_PAIN",
                "QOL_CHILD_HARD_TO_RUN_PAIN", "QOL_CHILD_HARD_TO_WALK_ONE_BLOCK_PAIN",

                "QOL_PATIENT_SPORT_EXERCISE", "QOL_PATIENT_GET_UP_FROM_FLOOR", "QOL_PATIENT_WALK_UP_STAIRS",
                "QOL_PATIENT_ABLE_TO_DO_ACTIVITIES", "QOL_PATIENT_AWFUL_MIGHT_HAPPEN", "QOL_PATIENT_FELT_NERVOUS",
                "QOL_PATIENT_FELT_WORRIED", "QOL_PATIENT_FELT_WORRIED_AT_HOME", "QOL_PATIENT_FELT_EVRTHNG_WENT_WRONG",
                "QOL_PATIENT_FELT_LONELY", "QOL_PATIENT_FELT_SAD", "QOL_PATIENT_HARD_TO_HAVE_FUN",
                "QOL_PATIENT_TIRED_TO_KEEP_UP", "QOL_PATIENT_GET_TIRED_EASILY", "QOL_PATIENT_TOO_TIRED_FOR_SPORT",
                "QOL_PATIENT_TOO_TIRED_TO_ENJOY", "QOL_PATIENT_FELT_ACCEPTED", "QOL_PATIENT_COUNT_ON_FRIENDS",
                "QOL_PATIENT_FRIENDS_HELP", "QOL_PATIENT_WANT_TO_BE_FRIENDS", "QOL_PATIENT_TROUBLE_SLEEPING_PAIN",
                "QOL_PATIENT_PAY_ATTENTION_PAIN", "QOL_PATIENT_HARD_TO_RUN_PAIN", "QOL_PATIENT_HARD_TO_WALK_ONE_BLOCK_PAIN"
        );
        addPicklistOption(handle, studyId, stableIds, option, 60);
        addValidation(handle, studyId, stableIds, ruleConfig);
        addAsteriskToQuestionContentBlocks(helper, handle, studyId, qolSelfId, "QUALITY_OF_LIFE");
        addAsteriskToQuestionContentBlocks(helper, handle, studyId, qolChildId, "CHILD_QUALITY_OF_LIFE");
        addAsteriskToQuestionContentBlocks(helper, handle, studyId, qolPatientId, "PATIENT_QUALITY_OF_LIFE");

        // REFID 38
        stableIds = Set.of("HD_GENETIC_TEST", "HD_GENETIC_TEST_REASON", "HD_GENETIC_TEST_REASON_PATIENT", "HD_GENETIC_TEST_REPORT",
                "HD_GENETIC_TEST_REPORT_PATIENT", "HD_PREGNANCY_ISSUES", "HD_LABOR_AND_DELIVERY_ISSUES", "HD_BRAIN_ISSUES",
                "HD_BEHAVIOR_ISSUES", "HD_GROWTH_ISSUES", "HD_CANCER_ISSUES", "HD_HEAD_NECK_ISSUES", "HD_EYES_ISSUES",
                "HD_EARS_ISSUES", "HD_SKIN_ISSUES", "HD_BONES_ISSUES", "HD_MUSCLES_ISSUES", "HD_HEART_ISSUES", "HD_LUNGS_ISSUES",
                "HD_DIGEST_ISSUES", "HD_HORMONES_ISSUES", "HD_GENITALS_ISSUES", "HD_IMMUNE_ISSUES", "HD_BLOOD_ISSUES",
                "HD_TEETH_ISSUES", "HD_WALK_ISSUES_PATIENT", "HD_DEVELOPMENT_CONCERNS_PATIENT", "HD_DEVELOPMENT_CONCERNS_M_Y_PATIENT",
                "HD_DEVELOPMENT_CONCERNS_MONTHS_PATIENT", "HD_DEVELOPMENT_CONCERNS_YEARS_PATIENT", "HD_REGRESSION_PATIENT",
                "HD_COMMUNICATION_ISSUES", "HD_LEARNING_DIFFERENCES", "HD_MOBILITY_ISSUES", "HD_SLEEPING_ISSUES", "HD_ENERGY_ISSUES",
                "HD_EMOTION_ISSUES", "HD_DEVELOPMENT_CONCERNS_M_Y", "HD_DEVELOPMENT_CONCERNS_MONTHS", "HD_DEVELOPMENT_CONCERNS_YEARS",
                "HD_FIRST_DEVELOPMENT_CONCERNS", "HD_WALK_ISSUES", "HD_DEVELOPMENT_CONCERNS", "HD_REGRESSION", "HD_PAIN",
                "HD_DIAGNOSIS_LIST");
        addValidation(handle, studyId, stableIds, ruleConfig);
        addAsteriskToQuestionContentBlocks(helper, handle, studyId, healthAndDevelopmentId, "HEALTH_AND_DEVELOPMENT");


        /*TextQuestionDef firstNameDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig("firstNameQuestion")), TextQuestionDef.class);
        QuestionBlockDef blockDef = new QuestionBlockDef(firstNameDef);
        sectionBlockDao.insertBlockForSection(activityId, fullNameSectionDto.getSectionId(), dobDisplayOrder - 5, blockDef, newV2RevId);*/


        //update template placeholder text
        /*String newTemplateText = "Your Signature (Full Name)*";
        long tmplVarId = helper.findTemplateVariableId(fullNameTextDto.getPlaceholderTemplateId());
        JdbiVariableSubstitution jdbiVarSubst = handle.attach(JdbiVariableSubstitution.class);
        List<Translation> transList = jdbiVarSubst.fetchSubstitutionsForTemplateVariable(tmplVarId);
        Translation currTranslation = transList.get(0);
        long newFullNameSubRevId = jdbiRevision.copyAndTerminate(currTranslation.getRevisionId().get(), meta);
        long[] revIds = {newFullNameSubRevId};
        jdbiVarSubst.bulkUpdateRevisionIdsBySubIds(Arrays.asList(currTranslation.getId().get()), revIds);
        jdbiVarSubst.insert(currTranslation.getLanguageCode(), newTemplateText, newV2RevId, tmplVarId);*/

    }

    private void addAsteriskToQuestionContentBlocks(SqlHelper helper, Handle handle, long studyId, Long activityId, String activityCode) {
        ActivityVersionDto currentVersionDto = jdbiVersion.getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find active version for activity " + activityId));
        FormActivityDef activity = findActivityDef(activityCode, currentVersionDto.getVersionTag());
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.CONTENT) {
                    var contentBlock = (ContentBlockDef) block;
                    Optional<TemplateVariable> questionVariable = contentBlock.getBodyTemplate().getVariable("question");
                    questionVariable.ifPresent(templateVariable -> helper.updateVarValueByTemplateVarId(
                            templateVariable.getId().get(),
                            templateVariable.getTranslation(LanguageStore.DEFAULT_LANG_CODE).get().getText() + "*"));
                }
            }
        }
    }

    private void replaceContentBlockVariableText(SqlHelper helper, long activityId, String variableName, String text) {
        List<Long> variableIds = helper.findContentBlockVariableIdsByVarName(activityId, variableName);
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
        }
    }

    private void replaceQuestionBlockVariableText(SqlHelper helper, long activityId, String stableId, String variableName, String text) {
        List<Long> variableIds = helper.findQuestionBlockVariableIdsByVarNameAndStableId(activityId, variableName, stableId);
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
        }
    }

    private void addPicklistOption(Handle handle, long studyId, Set<String> questionStableIds, Config option, int displayOrder) {
        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Stream<QuestionDto> questionDtos = jdbiQuestion.findLatestDtosByStudyIdAndQuestionStableIds(studyId, questionStableIds);
        questionDtos.forEach(questionDto -> plQuestionDao.insertOption(questionDto.getId(),
                gson.fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class), displayOrder,
                questionDto.getRevisionId()));
    }

    private void addValidation(Handle handle, long studyId, Collection<String> stableIds, Config ruleConfig) {
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        for (String stableId : stableIds) {
            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .findLatestDtoByStudyIdAndQuestionStableId(studyId, stableId)
                    .orElseThrow(() -> new DDPException("Could not find question " + stableId));
            RequiredRuleDef rule = gson.fromJson(ConfigUtil.toJson(ruleConfig), RequiredRuleDef.class);
            validationDao.insert(questionDto.getId(), rule, questionDto.getRevisionId());
            LOG.info("Inserted validation rule with id={} questionStableId={}", rule.getRuleId(), questionDto.getStableId());
        }
    }

    private FormActivityDef findActivityDef(String activityCode, String versionTag) {
        ActivityDto activityDto = jdbiActivity
                .findActivityByStudyIdAndCode(studyId, activityCode).get();
        ActivityVersionDto versionDto = jdbiVersion
                .findByActivityCodeAndVersionTag(studyId, activityCode, versionTag).get();
        return (FormActivityDef) activityDao
                .findDefByDtoAndVersion(activityDto, versionDto);
    }

    private interface SqlHelper extends SqlObject {

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        int findQuestionBlockId(@Bind("questionId") long questionId);

        @SqlQuery("select template_variable_id from template_variable where template_id = :templateId")
        long findTemplateVariableId(@Bind("templateId") long templateId);

        //WATCH OUT: might cause issue if same variable name/text across multiple studies.. ok for patch though.
        //used max to handle multiple study versions in lower regions
        @SqlQuery("select max(template_id) from template where template_text = :text")
        long findTemplateIdByTemplateText(@Bind("text") String text);

        @SqlUpdate("update block_group_header set list_style_hint_id = "
                + " (select list_style_hint_id from list_style_hint where list_style_hint_code = :listStyle)"
                + " where title_template_id = :templateId")
        int updateGroupHeaderStyleHintByTemplateId(@Bind("templateId") long templateId, @Bind("listStyle") String listStyle);

        @SqlUpdate("update form_section__block set display_order = :displayOrder where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockDisplayOrder(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("displayOrder") int displayOrder);

        @SqlUpdate("update i18n_activity_detail set subtitle = :text where study_activity_id = :studyActivityId")
        int update18nActivitySubtitle(@Bind("studyActivityId") long studyActivityId, @Bind("text") String text);

        @SqlQuery("select fsi.form_section_icon_id from form_section_icon fsi, form_activity__form_section fafs "
                + " where fafs.form_section_id = fsi.form_section_id "
                + " and fafs.form_activity_id = :studyActivityId")
        List<Long> findSectionIconIdByActivity(@Bind("studyActivityId") long studyActivityId);

        @SqlUpdate("delete from form_section_icon_source where form_section_icon_id in (<ids>)")
        int _deleteActivityIconSources(@BindList("ids") Set<Long> ids);

        @SqlUpdate("delete from form_section_icon where form_section_icon_id in (<ids>)")
        int _deleteActivityIcons(@BindList("ids") Set<Long> ids);

        default void deleteActivityIcons(Set<Long> ids) {
            int numUpdated = _deleteActivityIconSources(ids);
            if (numUpdated != 6) {
                throw new DDPException("Expected to delete 6 rows from icon sources ="
                        + " but deleted " + numUpdated);
            }

            numUpdated = _deleteActivityIcons(ids);
            if (numUpdated != 6) {
                throw new DDPException("Expected to delete 6 rows from form section icons ="
                        + " but deleted " + numUpdated);
            }
        }

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join block_content as bt on tmpl.template_id = bt.body_template_id"
                + " where tv.variable_name = :varName"
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
        List<Long> findContentBlockVariableIdsByVarName(@Bind("activityId") long activityId, @Bind("varName") String variableName);

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join picklist_question pk on tmpl.template_id = pk.picklist_label_template_id"
                + " join block__question bt on bt.question_id = pk.question_id"
                + " join question q on q.question_id = bt.question_id"
                + " join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + " where tv.variable_name = :varName"
                + "   and qsc.stable_id = :stableId"
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
        List<Long> findQuestionBlockVariableIdsByVarNameAndStableId(@Bind("activityId") long activityId,
                                                                    @Bind("varName") String variableName,
                                                                    @Bind("stableId") String stableId);

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join picklist_option po on tmpl.template_id = po.option_label_template_id"
                + " join picklist_question pk on po.picklist_question_id = pk.question_id"
                + " join block__question bt on bt.question_id = pk.question_id"
                + " join question q on q.question_id = bt.question_id"
                + " join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + " where qsc.stable_id = :questionStableId"
                + "   and po.picklist_option_stable_id = :optionStableId"
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
        List<Long> findPicklistOptionVariableIdsByStableIds(@Bind("activityId") long activityId,
                                                                    @Bind("optionStableId") String optionStableId,
                                                                    @Bind("questionStableId") String questionStableId);

        @SqlQuery("select tv.template_variable_id from template_variable tv"
                + " join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id"
                + " join template as tmpl on tmpl.template_id = tv.template_id"
                + " join block_content as bt on tmpl.template_id = bt.body_template_id"
                + " where ts.substitution_value = :text"
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
        List<Long> findVariableIdsByText(@Bind("activityId") long activityId,
                                         @Bind("text") String text);

        // For single language only
        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);


    }
}

package org.broadinstitute.ddp.studybuilder.task;

import com.google.gson.Gson;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.broadinstitute.ddp.cache.LanguageStore;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiActivityVersion;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.PicklistQuestionDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dao.SectionBlockDao;
import org.broadinstitute.ddp.db.dao.ValidationDao;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
public class RarexVersion2 implements CustomTask {
    private static final String STUDY_GUID = "rarex";
    private static final String DATA_FILE = "patches/patch_0721.conf";

    private Config studyCfg;
    private Gson gson;
    private Config dataCfg;
    private long studyId;

    private JdbiActivityVersion jdbiVersion;
    private ActivityDao activityDao;
    private JdbiActivity jdbiActivity;

    private SqlHelper helper;

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
        helper = handle.attach(SqlHelper.class);

        long generalInformationId = ActivityBuilder.findActivityId(handle, studyId, "GENERAL_INFORMATION");
        long healthAndDevelopmentId = ActivityBuilder.findActivityId(handle, studyId, "HEALTH_AND_DEVELOPMENT");
        long prequalId = ActivityBuilder.findActivityId(handle, studyId, "PREQUAL");
        long addParticipantId = ActivityBuilder.findActivityId(handle, studyId, "ADD_PARTICIPANT");
        long legacyId = ActivityBuilder.findActivityId(handle, studyId, "LEGACY");
        long qolSelfId = ActivityBuilder.findActivityId(handle, studyId, "QUALITY_OF_LIFE");
        long qolChildId = ActivityBuilder.findActivityId(handle, studyId, "CHILD_QUALITY_OF_LIFE");
        long qolPatientId = ActivityBuilder.findActivityId(handle, studyId, "PATIENT_QUALITY_OF_LIFE");

        log.info("REFID 27");
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_health_insurance_coverage_exp",
                "This is just to look at insurance coverage in rare disease in general, NOT to track insurance details.");

        log.info("REFID 71");
        updateVariableByText(healthAndDevelopmentId,
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function.",
                "Examples: physical mouth, lips, tongue or teeth issues or mouth function. "
                        + "You may have seen a dentist for these issues.");

        log.info("REFID 83");
        replaceQuestionBlockVariableText(prequalId, "SELF_COUNTRY",
                "COUNTRY_picklist_label",
                "Choose Country or Territory...");
        replaceQuestionBlockVariableText(addParticipantId, "PARTICIPANT_COUNTRY",
                "COUNTRY_picklist_label",
                "Choose Country or Territory...");

        log.info("REFID 98");
        updateVariableByText(healthAndDevelopmentId,
                "Have you ever been diagnosed with cancer, colon polyps or a non-cancerous tumor?",
                "Have you ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?");
        updateVariableByText(healthAndDevelopmentId,
                "Has the patient ever been diagnosed with cancer, colon polyps or a non-cancerous tumor? ",
                "Has the patient ever been diagnosed with CANCER, COLON POLYPS or a NON-CANCEROUS TUMOR?");
        updateVariableByText(healthAndDevelopmentId,
                "Have you had issues with your teeth or mouth?",
                "Have you had issues with your TEETH or MOUTH?");
        updateVariableByText(healthAndDevelopmentId,
                "Has the patient had issues with their teeth or mouth?",
                "Has the patient had issues with their TEETH or MOUTH?");

        log.info("REFID 105");
        updateVariableByText(healthAndDevelopmentId,
                "For the conditions you listed above do you have reports or summaries to upload as support?",
                "Do you have genetic reports or summaries to upload?");

        log.info("REFID 99");
        Map<Long, String> stableCodes = Map.of(
                addParticipantId, "PRIMARY_DIAGNOSIS",
                legacyId, "LEGACY_PRIMARY_DIAGNOSIS");
        updatePicklistOptionVariables(stableCodes, "DYRK1",
                "DYRK1A - dual-specificity tyrosine phosphorylation regulated kinase 1A");

        log.info("REFID 101");
        Config option = dataCfg.getConfig("stateDC");
        addPicklistOption(handle, studyId, Set.of("PARTICIPANT_STATE", "SELF_STATE"), option, 75);

        log.info("REFID 107");
        option = dataCfg.getConfig("preferNotToAnswer");
        Config ruleConfig = dataCfg.getConfig("qolRequiredValidation");
        Set<String> stableIds = new HashSet<>(Set.of("QOL_SELF_WALK", "QOL_SELF_ERRANDS", "QOL_SELF_FEARFUL", "QOL_SELF_FOCUS",
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
        ));
        addPicklistOption(handle, studyId, stableIds, option, 60);
        stableIds.addAll(Set.of("QOL_SELF_PAIN_INTENSITY", "QOL_CHILD_PAIN_INTENSITY", "QOL_PATIENT_PAIN_INTENSITY"));
        addValidation(handle, studyId, stableIds, ruleConfig);
        addAsteriskToQuestionContentBlocks(qolSelfId, "QUALITY_OF_LIFE");
        addAsteriskToQuestionContentBlocks(qolChildId, "CHILD_QUALITY_OF_LIFE");
        addAsteriskToQuestionContentBlocks(qolPatientId, "PATIENT_QUALITY_OF_LIFE");
        addBlockToActivity(handle, "QUALITY_OF_LIFE", qolSelfId, 7,
                "preferNotToAnswerCheckboxSelf");
        addBlockToActivity(handle, "CHILD_QUALITY_OF_LIFE", qolChildId, 6,
                "preferNotToAnswerCheckboxChild");
        addBlockToActivity(handle, "PATIENT_QUALITY_OF_LIFE", qolPatientId, 6,
                "preferNotToAnswerCheckboxPatient");
        updateExpressionTextByQuestionStableCode(qolSelfId, "QOL_SELF_PAIN_INTENSITY",
                "!user.studies[\"rarex\"].forms[\"QUALITY_OF_LIFE\"].questions[\"PAIN_INTENSITY_SELF_PREFERENCE\"].answers."
                        + "hasOption(\"PREFER_NOT_TO_ANSWER\")");
        updateExpressionTextByQuestionStableCode(qolChildId, "QOL_CHILD_PAIN_INTENSITY",
                "!user.studies[\"rarex\"].forms[\"CHILD_QUALITY_OF_LIFE\"].questions[\"PAIN_INTENSITY_CHILD_PREFERENCE\"].answers."
                        + "hasOption(\"PREFER_NOT_TO_ANSWER\")");
        updateExpressionTextByQuestionStableCode(qolPatientId, "QOL_PATIENT_PAIN_INTENSITY",
                "!user.studies[\"rarex\"].forms[\"PATIENT_QUALITY_OF_LIFE\"].questions[\"PAIN_INTENSITY_PATIENT_PREFERENCE\"].answers."
                        + "hasOption(\"PREFER_NOT_TO_ANSWER\")");

        log.info("REFID 103");
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
        addAsteriskToQuestionContentBlocks(healthAndDevelopmentId, "HEALTH_AND_DEVELOPMENT");
        updateExpressionTextByQuestionStableCode(healthAndDevelopmentId, "HD_GENETIC_TEST_REASON",
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers"
                        + ".hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionTextByQuestionStableCode(healthAndDevelopmentId, "HD_GENETIC_TEST_REASON_PATIENT",
                "!user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers"
                        + ".hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionTextByQuestionStableCode(healthAndDevelopmentId, "HD_GENETIC_TEST_REPORT",
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionTextByQuestionStableCode(healthAndDevelopmentId, "HD_GENETIC_TEST_REPORT_PATIENT",
                "!user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionText(healthAndDevelopmentId,
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasAnyOption(\"YES\",\"UNSURE\")",
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasOption(\"SELF\")\n            && user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"]."
                        + "questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionText(healthAndDevelopmentId,
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasAnyOption(\"PARENT\", \"GUARDIAN\")\n            && user.studies[\"rarex\"]"
                        + ".forms[\"HEALTH_AND_DEVELOPMENT\"].questions[\"HD_GENETIC_TEST\"].answers.hasAnyOption(\"YES\",\"UNSURE\")",
                "user.studies[\"rarex\"].forms[\"ADD_PARTICIPANT\"].questions[\"PARTICIPANT_RELATIONSHIP\"].answers."
                        + "hasAnyOption(\"PARENT\", \"GUARDIAN\")\n            && user.studies[\"rarex\"]"
                        + ".forms[\"HEALTH_AND_DEVELOPMENT\"].questions[\"HD_GENETIC_TEST\"].answers.hasOption(\"YES\")");
        updateExpressionText(healthAndDevelopmentId,
                "user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"].questions[\"HD_GENETIC_TEST\"].answers."
                        + "hasAnyOption(\"YES\",\"UNSURE\")",
                "user.studies[\"rarex\"].forms[\"HEALTH_AND_DEVELOPMENT\"].questions[\"HD_GENETIC_TEST\"].answers."
                        + "hasOption(\"YES\")");

        log.info("REFID 109");
        long consentSelfId = ActivityBuilder.findActivityId(handle, studyId, "CONSENT");
        long consentParentalId = ActivityBuilder.findActivityId(handle, studyId, "PARENTAL_CONSENT");
        long consentAssentId = ActivityBuilder.findActivityId(handle, studyId, "CONSENT_ASSENT");
        replaceContentBlockVariableTextByName(consentSelfId,
                "rarex_consent_s2_purpose_detail",
                "The DCP is a program to collect and store data about participants with lots of different kinds of rare "
                        + "diseases for research and participant support. Another purpose of the DCP is to increase participant "
                        + "recruitment into research studies and clinical trials.");
        updateVariableByText(consentSelfId,
                "\n"
                        + "              I give permission to the RARE-X DCP’s staff to contact me"
                        + " to ask me to update my health status, or my\n"
                        + "              contact information, to request that I upload a particular "
                        + "attachment or to complete forms associated with\n"
                        + "              my participation in the DCP.\n            ",
                "I give permission to the RARE-X DCP’s study staff to contact me to ask me to update my health status, or my "
                        + " contact information, to request that I upload a particular attachment or to complete forms associated with "
                        + " my participation in the DCP.");
        updateVariableByText(consentSelfId,
                "\n              You are not likely to directly benefit from participating in this Program.\n            ",
                "You are not likely to directly benefit from participating in this program.");
        updateVariableByText(consentParentalId,
                "\n              Your child is not likely to directly benefit from participating in this Program.\n            ",
                "Your child is not likely to directly benefit from participating in this program.");
        updateVariableByText(consentAssentId,
                "\n              Your child is not likely to directly benefit from participating in this Program.\n            ",
                "Your child is not likely to directly benefit from participating in this program.");

        log.info("REFID 71");
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_birthplace_header",
                "Birthplace");
        detachQuestionFromBothSectionAndBlock(handle, "PATIENT_DATE_OF_BIRTH");

        log.info("REFID 92");
        helper.updateExpressionText(
                "user.studies[\"rarex\"].forms[\"DATA_SHARING\"].questions"
                        + "[\"DATA_SHARING_BIOSPECIMEN_INTERESTED\"].answers.hasOption(\"YES\")",
                helper.findComponentBlockExpressionIds(generalInformationId));

        log.info("REFID 28");
        helper.updatePicklistsToMulti(healthAndDevelopmentId, List.of("HD_GENETIC_TEST_REASON_PATIENT", "HD_GENETIC_TEST_REASON"));

        log.info("REFID 102");
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_primary_lang_prompt",
                "What is the primary language you speak?*");
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_patient_primary_lang_prompt",
                "What is the primary language the patient speaks?*");
        addValidation(handle, studyId, List.of("GI_PRIMARY_LANGUAGE"), ruleConfig);
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_hic_not_to_answer_details",
                "What type(s) of coverage do you have?*");

        log.info("REFID 48");
        replaceContentBlockVariableTextByName(generalInformationId,
                "general_information_hic_not_to_answer_details_exp",
                "For health insurance coverage outside the USA, please select the categories that most closely "
                        + "describe the source of the participant's health insurance.");
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Optional<QuestionDto> questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId,
                "GENERAL_INFORMATION_HIC_OTHER_DETAIL");
        long blockId = detachQuestionFromBlockAndGetBlockId(handle, "GENERAL_INFORMATION_HIC_OTHER_DETAIL");
        PicklistQuestionDef questionDef = (PicklistQuestionDef) gson.fromJson(
                ConfigUtil.toJson(dataCfg.getConfig("GIInsuranceQuestionBlock")), QuestionDef.class);
        QuestionDao questionDao = handle.attach(QuestionDao.class);
        questionDao.insertQuestion(generalInformationId, questionDef, questionDto.get().getRevisionId());
        questionDao.getJdbiBlockQuestion().insert(blockId, questionDef.getQuestionId());

        log.info("REFID 30");
        blockId = helper.findBlockByText(healthAndDevelopmentId,
                "While your mom was pregnant with you do you know if there were any concerns with the pregnancy or "
                        + "have you been told that there were issues during <u>your biological mother's</u> PREGNANCY with you?*");
        helper.updateBlockVariable(blockId, "question", "Have you been told that there were issues during <u>your biological "
                + "mother's</u> PREGNANCY with you and/or your DELIVERY/BIRTH?*");
        helper.updateBlockVariable(blockId, "explanation", "Examples: Mother had to be put on bed rest, "
                + "there were concerns on ultrasound, you were born early (prematurely), or you had to stay in the neonatal "
                + "intensive care unit (NICU).");
        blockId = helper.findBlockByText(healthAndDevelopmentId,
                "During the pregnancy with this patient do you know if there were any concerns with the pregnancy or "
                        + "have you been told that there were issues during the biological mother's pregnancy?*");
        helper.updateBlockVariable(blockId, "question", "Have you been told that there were issues during <u>"
                + "the patient's biological mother's</u> PREGNANCY with the patient and/or the patient DELIVERY/BIRTH?*");
        helper.updateBlockVariable(blockId, "explanation", "Examples: Mother had to be put on bed rest, "
                + "there were concerns on ultrasound, the patient was born early (prematurely), or the patient had to stay "
                + "in the neonatal intensive care unit (NICU).");
        blockId = helper.findBlockByText(healthAndDevelopmentId, "Examples: You were born early (prematurely), or you "
                + "had to stay in the neonatal intensive care unit (NICU).");
        helper._deleteSectionBlockMembershipByBlockId(blockId);
        blockId = helper.findBlockByText(healthAndDevelopmentId, "Examples: The patient was born early (prematurely), or "
                + "had to stay in the neonatal intensive care unit (NICU).");
        helper._deleteSectionBlockMembershipByBlockId(blockId);
        detachQuestionFromBothSectionAndBlock(handle, "HD_LABOR_AND_DELIVERY_ISSUES");
    }

    private void updateExpressionTextByQuestionStableCode(long activityId, String questionStableCode, String expr) {
        List<Long> ids = helper.findBlockExpressionIdsByQuestionStableCode(activityId, questionStableCode);
        helper.updateExpressionText(expr, ids);
    }

    private void updateExpressionText(long activityId, String before, String after) {
        List<Long> ids = helper.findBlockExpressionIdsByExpressionText(activityId, before);
        helper.updateExpressionText(after, ids);
    }

    private void updatePicklistOptionVariables(Map<Long, String> stableCodes, String optionStableCode, String replacement) {
        List<Long> varIds = new ArrayList<>();
        for (Map.Entry<Long, String> entry : stableCodes.entrySet()) {
            varIds.addAll(helper.findPicklistOptionVariableIdsByStableIds(entry.getKey(), optionStableCode, entry.getValue()));
        }
        varIds.forEach(varId -> {
            helper.updateVarValueByTemplateVarId(varId, replacement);
            log.info("Updated picklist option {} template variable {} text to \"{}\"", optionStableCode, varId, replacement);
        });
    }

    private void updateVariableByText(long activityId, String before, String after) {
        List<Long> varIds = helper.findVariableIdsByText(activityId, before);
        if (CollectionUtils.isEmpty(varIds)) {
            throw new DDPException("Could not find any variable with text " + before);
        }
        varIds.forEach(varId -> {
            helper.updateVarValueByTemplateVarId(varId, after);
            log.info("Template variable {} text was updated from \"{}\" to \"{}\"", varId, before, after);
        });
    }

    private void addBlockToActivity(Handle handle, String activityGuid, long activityId, int sectionNumber, String blockName) {
        ActivityDto activityDto = handle.attach(JdbiActivity.class)
                .findActivityByStudyGuidAndCode(STUDY_GUID, activityGuid).get();
        ActivityVersionDto ver = jdbiVersion.getActiveVersion(activityId).get();
        FormActivityDef currentDef = (FormActivityDef) activityDao.findDefByDtoAndVersion(activityDto, ver);
        FormSectionDef currentSectionDef = currentDef.getSections().get(sectionNumber);
        SectionBlockDao sectionBlockDao = handle.attach(SectionBlockDao.class);
        FormBlockDef raceDef = gson.fromJson(ConfigUtil.toJson(dataCfg.getConfig(blockName)), FormBlockDef.class);
        int displayOrder = currentSectionDef.getBlocks().size() * 10 + 10;
        sectionBlockDao.insertBlockForSection(activityId, currentSectionDef.getSectionId(),
                displayOrder, raceDef, ver.getRevId());
        log.info("New block {} was added to activity {} into section #{} with display order {}", blockName,
                activityGuid, sectionNumber, displayOrder);
    }

    private void detachQuestionFromBothSectionAndBlock(Handle handle, String questionStableId) {
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Optional<QuestionDto> questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId);
        if (questionDto.isEmpty()) {
            throw new DDPException("Couldn't find question with stableId: " + questionStableId);
        }
        helper.detachQuestionFromBothSectionAndBlock(questionDto.get().getId());
        log.info("Question {} and its block were detached", questionStableId);
    }

    private long detachQuestionFromBlockAndGetBlockId(Handle handle, String questionStableId) {
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Optional<QuestionDto> questionDto = jdbiQuestion.findLatestDtoByStudyIdAndQuestionStableId(studyId, questionStableId);
        if (questionDto.isPresent()) {
            long questionId = questionDto.get().getId();
            long blockId = helper.findQuestionBlockId(questionId);
            helper.detachQuestionFromBlock(questionId);
            log.info("Question {} was detached from block {}", questionStableId, blockId);
            return blockId;
        }
        throw new DDPException("Couldn't find question with stableId: " + questionStableId);
    }

    private void addAsteriskToQuestionContentBlocks(Long activityId, String activityCode) {
        ActivityVersionDto currentVersionDto = jdbiVersion.getActiveVersion(activityId)
                .orElseThrow(() -> new DDPException("Could not find active version for activity " + activityId));
        FormActivityDef activity = findActivityDef(activityCode, currentVersionDto.getVersionTag());
        for (var section : activity.getAllSections()) {
            for (var block : section.getBlocks()) {
                if (block.getBlockType() == BlockType.CONTENT) {
                    var contentBlock = (ContentBlockDef) block;
                    Optional<TemplateVariable> questionVariable = contentBlock.getBodyTemplate().getVariable("question");
                    questionVariable.ifPresent(templateVariable -> {
                        helper.updateVarValueByTemplateVarId(
                                templateVariable.getId().get(),
                                templateVariable.getTranslation(LanguageStore.DEFAULT_LANG_CODE).get().getText() + "*");
                        log.info("Added an asterisk to the content block variable {} with id {}",
                                templateVariable.getName(), templateVariable.getId());
                    });
                }
            }
        }
    }

    private void replaceContentBlockVariableTextByName(long activityId, String variableName, String text) {
        List<Long> variableIds = helper.findContentBlockVariableIdsByVarName(activityId, variableName);
        if (CollectionUtils.isEmpty(variableIds)) {
            throw new DDPException("Could not find any variable with name " + variableName);
        }
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
            log.info("Updated content block variable {} with text \"{}\"", variableId, text);
        }
    }

    private void replaceQuestionBlockVariableText(long activityId, String stableId, String variableName, String text) {
        List<Long> variableIds = helper.findQuestionBlockVariableIdsByVarNameAndStableId(activityId, variableName, stableId);
        if (CollectionUtils.isEmpty(variableIds)) {
            throw new DDPException("Could not find any variable with name " + variableName);
        }
        for (Long variableId : variableIds) {
            helper.updateVarValueByTemplateVarId(variableId, text);
            log.info("Updated question {} block variable {} in activity {} with text \"{}\"", stableId, variableId, activityId, text);
        }
    }

    private void addPicklistOption(Handle handle, long studyId, Set<String> questionStableIds, Config option, int displayOrder) {
        PicklistQuestionDao plQuestionDao = handle.attach(PicklistQuestionDao.class);
        JdbiQuestion jdbiQuestion = handle.attach(JdbiQuestion.class);
        Stream<QuestionDto> questionDtos = jdbiQuestion.findLatestDtosByStudyIdAndQuestionStableIds(studyId, questionStableIds);
        questionDtos.forEach(questionDto -> {
            PicklistOptionDef pickListOptionDef = gson.fromJson(ConfigUtil.toJson(option), PicklistOptionDef.class);
            plQuestionDao.insertOption(questionDto.getId(), pickListOptionDef, displayOrder, questionDto.getRevisionId());
            log.info("Added new picklistOption " + pickListOptionDef.getStableId() + " with id "
                    + pickListOptionDef.getOptionId() + " into question " + questionDto.getStableId());
        });
    }

    private void addValidation(Handle handle, long studyId, Collection<String> stableIds, Config ruleConfig) {
        ValidationDao validationDao = handle.attach(ValidationDao.class);
        for (String stableId : stableIds) {
            QuestionDto questionDto = handle.attach(JdbiQuestion.class)
                    .findLatestDtoByStudyIdAndQuestionStableId(studyId, stableId)
                    .orElseThrow(() -> new DDPException("Could not find question " + stableId));
            RequiredRuleDef rule = gson.fromJson(ConfigUtil.toJson(ruleConfig), RequiredRuleDef.class);
            validationDao.insert(questionDto.getId(), rule, questionDto.getRevisionId());
            log.info("Inserted validation rule with id={} questionStableId={}", rule.getRuleId(), questionDto.getStableId());
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

        @SqlUpdate("update form_section__block set display_order = :displayOrder where form_section__block_id = :formSectionBlockId")
        int updateFormSectionBlockDisplayOrder(@Bind("formSectionBlockId") long formSectionBlockId, @Bind("displayOrder") int displayOrder);

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
                + " join block_content as bt on tmpl.template_id = bt.body_template_id or tmpl.template_id = bt.title_template_id"
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

        @SqlQuery("select e.expression_id from block_component as bt "
                + "   join block__expression be on be.block_id = bt.block_id"
                + "   join expression e on e.expression_id = be.expression_id"
                + " where bt.block_id in "
                + " (select fsb.block_id"
                + " from form_activity__form_section as fafs"
                + " join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + " where fafs.form_activity_id = :activityId)")
        List<Long> findComponentBlockExpressionIds(@Bind("activityId") long activityId);

        @SqlQuery("select e.expression_id from block__question as bt "
                + "left join block__expression be on be.block_id = bt.block_id "
                + "join expression e on e.expression_id = be.expression_id "
                + "join question q on q.question_id = bt.question_id "
                + "join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id "
                + "  where qsc.stable_id = :stableId and "
                + "  bt.block_id in "
                + "  (select fsb.block_id "
                + "  from form_activity__form_section as fafs "
                + "  join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id "
                + "  where fafs.form_activity_id = :activityId)")
        List<Long> findBlockExpressionIdsByQuestionStableCode(@Bind("activityId") long activityId,
                                                              @Bind("stableId") String stableId);

        @SqlQuery("select e.expression_id from block as bt "
                + "left join block__expression be on be.block_id = bt.block_id "
                + "join expression e on e.expression_id = be.expression_id "
                + "  where "
                + "  e.expression_text = :expr and "
                + "  bt.block_id in "
                + "  (select fsb.block_id "
                + "  from form_activity__form_section as fafs  "
                + "  join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id "
                + "  where fafs.form_activity_id = :activityId)")
        List<Long> findBlockExpressionIdsByExpressionText(@Bind("activityId") long activityId,
                                                              @Bind("expr") String expr);

        // For single language only
        @SqlUpdate("update i18n_template_substitution set substitution_value = :value where template_variable_id = :id")
        int updateVarValueByTemplateVarId(@Bind("id") long templateVarId, @Bind("value") String value);

        default void detachQuestionFromBlock(long questionId) {
            int numDeleted = _deleteBlockQuestionByQuestionId(questionId);
            if (numDeleted != 1) {
                throw new DDPException("Could not remove question with questionId=" + questionId + " from block");
            }
        }

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

        @SqlUpdate("delete from form_section__block where block_id = :blockId")
        int _deleteSectionBlockMembershipByBlockId(@Bind("blockId") long blockId);

        @SqlUpdate("delete from block__question where question_id = :questionId")
        int _deleteBlockQuestionByQuestionId(@Bind("questionId") long questionId);

        @SqlQuery("select block_id from block__question where question_id = :questionId")
        Long getBlockIdByQuestionId(@Bind("questionId") long questionId);

        @SqlUpdate("update expression set expression_text = :text where expression_id in (<ids>)")
        int updateExpressionText(@Bind("text") String text,
                                  @BindList(value = "ids", onEmpty = BindList.EmptyHandling.THROW) List<Long> ids);

        @SqlUpdate("update picklist_question pk"
                + " join block__question bt on bt.question_id = pk.question_id"
                + " join question q on q.question_id = bt.question_id"
                + " join question_stable_code qsc on qsc.question_stable_code_id = q.question_stable_code_id"
                + " set pk.picklist_select_mode_id = "
                + " (select picklist_select_mode_id from picklist_select_mode where picklist_select_mode_code='MULTIPLE')"
                + "                where"
                + " qsc.stable_id in (<stableIds>)"
                + "   and bt.block_id in "
                + "(select fsb.block_id"
                + " from form_activity__form_section as fafs"
                + " join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id"
                + " where fafs.form_activity_id = :activityId)")
        int updatePicklistsToMulti(@Bind("activityId") long activityId,
                                   @BindList(value = "stableIds", onEmpty = BindList.EmptyHandling.NULL) List<String> stableIds);

        @SqlQuery("select bt.block_id from template_variable tv "
                + "join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id "
                + "join template as tmpl on tmpl.template_id = tv.template_id "
                + "join block_content as bt on tmpl.template_id = bt.body_template_id "
                + "where ts.substitution_value = :text and "
                + " bt.block_id in (select fsb.block_id "
                + " from form_activity__form_section as fafs "
                + " join form_section__block as fsb on fsb.form_section_id = fafs.form_section_id "
                + " where fafs.form_activity_id = :activityId)")
        Long findBlockByText(@Bind("activityId") long activityId, @Bind("text") String text);

        @SqlUpdate("update template_variable tv "
                + "join i18n_template_substitution ts on ts.template_variable_id = tv.template_variable_id "
                + "join template as tmpl on tmpl.template_id = tv.template_id "
                + "join block_content as bt on tmpl.template_id = bt.body_template_id "
                + "set ts.substitution_value = :text "
                + "where "
                + " tv.variable_name = :varName and "
                + " bt.block_id = :blockId")
        void updateBlockVariable(@Bind("blockId") long blockId, @Bind("varName") String varName, @Bind("text") String text);
    }
}

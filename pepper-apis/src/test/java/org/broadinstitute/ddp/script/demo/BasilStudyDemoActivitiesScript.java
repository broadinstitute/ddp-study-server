package org.broadinstitute.ddp.script.demo;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.GsonBuilder;
import org.apache.commons.lang3.StringUtils;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.constants.SqlConstants;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.DBUtils;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ConsentActivityDao;
import org.broadinstitute.ddp.db.dao.EventActionDao;
import org.broadinstitute.ddp.db.dao.EventTriggerDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiBlock;
import org.broadinstitute.ddp.db.dao.JdbiEventConfiguration;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiFormSection;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityDto;
import org.broadinstitute.ddp.db.dto.BlockDto;
import org.broadinstitute.ddp.db.dto.FormSectionDto;
import org.broadinstitute.ddp.db.dto.SendgridEmailEventActionDto;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.ConfigUtil;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class BasilStudyDemoActivitiesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(BasilStudyDemoActivitiesScript.class);

    private static final String USER_GUID = TestConstants.TEST_USER_GUID;
    private static final String BASIL_STUDY_GUID = TestConstants.TEST_STUDY_GUID;
    private static final String PREQUAL_ACT_CODE = "7A838F4669";

    private static final String CONSENT_ACT_CODE = "1S2G7MIPZT";
    private static final String PEX_STUDY_ACTIVITY_QUESTION_BASE = "user.studies[\"" + TestConstants.TEST_STUDY_GUID
            + "\"].forms[\"" + CONSENT_ACT_CODE + "\"].questions[\"%s\"].answers";
    private static final String I_AM_AT_LEAST_21_YEARS_OF_AGE = "I am at least 21 years of age.";
    private static final String PREAMBLE_TEXT = "<p>I am being asked to participate in the Basil Study. "
            + "Being in a research study is voluntary. Before I decide whether I want to participate, I should "
            + "know why the research is being done and what it involves. I may print out a copy of this "
            + "form and talk about it with family or friends before making a decision. I can email or call"
            + " the study doctor, Dr. Spice at spice@basil.org with any questions. If I have questions that have "
            + "not been answered to my satisfaction, I should not sign or press submit at the end of this form.</p>"
            + "<p>Basil is an ovarian cancer research study that seeks to better understand  the causes of ovarian "
            + "cancer</p><p>By signing this Informed Consent form, you agree to participate in the Basil study.  "
            + "You attest that you are at least 21 years old, live in the United States, and have been diagnosed with "
            + "ovarian cancer at some point in your life.  You may leave the study at any time by contacting study "
            + "staff at the email above.</p>";
    private static final String AGREE_TO_PARTICIPATE = "I agree to participate in the Basil study";
    private static final String SHARING_MEDICAL_RECORDS = "Sharing Medical Records";
    private static final String WILLING_TO_SHARE = "I am willing to share my medical records with the Basil study.";
    private static final String SHARING_GENETIC_INFORMATION = "Sharing Genetic Information";
    private static final String SHARE_MY_SEQUENCE = "I’d like to share my genetic information obtained from a sample"
            + " of my saliva (spit). If I check “Yes” my genetic information will be analyzed from the DNA in my "
            + "saliva. We will store your sample until the genetic testing is successfully completed.";
    private static final String SIGNATURE = "Signature";
    private static final String DATE_OF_BIRTH = "Date of Birth";
    private static final String MAIN_CONSENT = "Main Consent";
    private static final String MAIN_CONSENT_DASHBOARD_NAME = "Consent (Main)";
    private static final String MAIN_CONSENT_DESCRIPTION = "This activity is about getting the participant's consent";
    private static final String MAIN_CONSENT_DASHBOARD_SUMMARY = "This activity is about getting the participant's consent";

    @Test
    @Ignore
    public void insertEmailNotificationConfig() {
        String testingTemplate = ConfigUtil.getGenericSendgridTestingTemplate(cfg).getString(ConfigFile.Sendgrid
                .TEMPLATE);
        TransactionWrapper.useTxn(handle -> {
            long basilStudyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(BASIL_STUDY_GUID).get();
            long consentActId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(basilStudyId, CONSENT_ACT_CODE).get();

            long triggerId = handle.attach(EventTriggerDao.class).insertStatusTrigger(consentActId, InstanceStatusType.COMPLETE);
            SendgridEmailEventActionDto eventAction = new SendgridEmailEventActionDto(testingTemplate, "en");
            long actionId = handle.attach(EventActionDao.class).insertNotificationAction(eventAction);

            JdbiExpression jdbiExpr = handle.attach(JdbiExpression.class);
            long preconditionExprId = jdbiExpr.insertExpression("true").getId();
            long cancelExprId = jdbiExpr.insertExpression("false").getId();

            long configId = handle.attach(JdbiEventConfiguration.class).insert(triggerId, actionId, basilStudyId,
                    Instant.now().toEpochMilli(), 1, 0, preconditionExprId, cancelExprId, true, 1);

            LOG.info("Created event configuration with id {} for post-consent email notification using sendgrid template {}",
                    configId, testingTemplate);
        });
    }

    @Test
    @Ignore
    public void insertDemoPrequalifier() {
        ContentBlockDef intro = new ContentBlockDef(Template.html("<h1>Join Basil!</h1>"),
                Template.html("<p><em>Are you interested in supporting ovarian cancer research that could help others?</em></p>"
                        + "<p>If you are at least 21 years old, of any race or ethnicity, live in the United States, have ever been"
                        + " diagnosed with ovarian cancer, and have access to the internet, then you are eligible to join Basil.</p>"
                        + "<p><strong>Ready to join? Get started by answering the simple questions below.</strong></p>"));

        QuestionBlockDef stateQuestion = new QuestionBlockDef(BoolQuestionDef.builder("BASIL_Q_STATES",
                Template.html("<p>Do you live in the United States?</p>"), Template.text("Yes"), Template.text("No")).build());

        QuestionBlockDef otherCountryQuestion = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT,
                "BASIL_Q_OTHER_COUNTRY", Template.html("<p>What country do you live in?</p>")).build());
        otherCountryQuestion.setShownExpr(String.format(
                "user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasFalse()",
                BASIL_STUDY_GUID, PREQUAL_ACT_CODE, "BASIL_Q_STATES"));

        QuestionBlockDef femaleQuestion = new QuestionBlockDef(BoolQuestionDef.builder("BASIL_Q_AGE",
                Template.html("<p>Are you female?</p>"), Template.text("Yes"), Template.text("No")).build());

        QuestionBlockDef diagnosedQuestion = new QuestionBlockDef(BoolQuestionDef.builder("BASIL_Q_DIAGNOSED",
                Template.html("<p>Have you ever been diagnosed with ovarian cancer?</p>"),
                Template.text("Yes"), Template.text("No")).build());

        QuestionBlockDef ageRangeQuestion = new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.LIST, "PEPPER_TEST_PREQUAL_AGE_RANGE",
                        Template.html("<p>Which group represents your age?</p>"))
                .addOption(new PicklistOptionDef("21-34", Template.text("21- to 34-years old")))
                .addOption(new PicklistOptionDef("35-44", Template.text("35- to 44-years old")))
                .addOption(new PicklistOptionDef("45-59", Template.text("45- to 59-years old")))
                .addOption(new PicklistOptionDef("60-74", Template.text("60- to 74-years old")))
                .addOption(new PicklistOptionDef("75+", Template.text("75-years old and up")))
                .build());

        QuestionBlockDef howHearQuestion = new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.DROPDOWN, "BASIL_Q_HOW_HEAR",
                        Template.html("<p>How did you hear about this study?</p>"))
                .setLabel(Template.text("Select option or give your answer"))
                .addOption(new PicklistOptionDef("HOW_HEAR_HEALTHCARE_PROVIDER", Template.text("From my healthcare provider")))
                .addOption(new PicklistOptionDef("HOW_HEAR_OTHER_PARTICIPANT", Template.text("From another participant in the study")))
                .addOption(new PicklistOptionDef("HOW_HEAR_FRIENDS", Template.text("From friends/family not in the study")))
                .addOption(new PicklistOptionDef("HOW_HEAR_AD", Template.text("From an ad")))
                .addOption(new PicklistOptionDef("HOW_HEAR_ONLINE", Template.text("By searching online")))
                .addOption(new PicklistOptionDef("OTHER", Template.text("Other option..."), Template.text("Your variant")))
                .build());

        FormSectionDef body = new FormSectionDef(null, Arrays.asList(intro, stateQuestion, otherCountryQuestion,
                femaleQuestion, diagnosedQuestion, ageRangeQuestion, howHearQuestion));

        FormActivityDef prequal = FormActivityDef.formBuilder(FormType.PREQUALIFIER, PREQUAL_ACT_CODE, "v1", BASIL_STUDY_GUID)
                .addName(new Translation("en", "Join the Basil Research Study!"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(1)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert basil demo prequalifier");
            handle.attach(ActivityDao.class).insertActivity(prequal, meta);

            assertNotNull(prequal.getActivityId());
            LOG.info("Created basil demo prequalifier activity code={} id={} json=\n{}", PREQUAL_ACT_CODE, prequal.getActivityId(),
                    GsonUtil.standardGson().toJson(prequal));
        });
    }

    private Template createTemplate(String templateVariable,
                                    String templateCode,
                                    Map<String, String> translations,
                                    boolean bigText) {
        String template = "<p>" + Template.VELOCITY_VAR_PREFIX + templateVariable + "</p>";
        if (bigText) {
            template = "<h1>" + Template.VELOCITY_VAR_PREFIX + templateVariable + "</h1>";
        }
        return createTemplate(templateVariable, templateCode, translations, template);
    }

    private Template createTemplate(String templateVariable,
                                    String templateCode,
                                    Map<String, String> translations,
                                    String templateText) {
        Template questionPrompt = new Template(TemplateType.HTML, templateCode, templateText);
        List<Translation> translationList = new ArrayList<>();
        for (Map.Entry<String, String> translation : translations.entrySet()) {
            translationList.add(new Translation(translation.getKey(), translation.getValue()));
        }
        TemplateVariable questionTemplateVar = new TemplateVariable(templateVariable, translationList);
        questionPrompt.addVariable(questionTemplateVar);
        return questionPrompt;
    }

    private Template buildConsentPremableTemplate() {
        String templateVar = "var" + System.currentTimeMillis();
        Template template = new Template(TemplateType.HTML, "Test." + System.currentTimeMillis(),
                "$" + templateVar + "");
        TemplateVariable templateVariable = new TemplateVariable(templateVar,
                Collections.singletonList(new Translation("en", PREAMBLE_TEXT)));
        template.addVariable(templateVariable);
        return template;
    }

    private Template buildHorzintalDivTemplate(String title, String templateCode) {
        String templateVar = "var" + System.currentTimeMillis();
        Template template = new Template(TemplateType.HTML, templateCode, "<h1>$" + templateVar + "</h1><hr>");
        TemplateVariable templateVariable = new TemplateVariable(templateVar,
                Collections.singletonList(new Translation("en", title)));
        template.addVariable(templateVariable);
        return template;
    }

    private Template buildValidationHintTemplate(String hintTemplatePrefix) {
        String templateVar = "var" + System.currentTimeMillis();
        Template template = new Template(TemplateType.HTML, hintTemplatePrefix + ".hint."
                + System.currentTimeMillis(), "<p>$" + templateVar + "</p>");
        TemplateVariable templateVariable = new TemplateVariable(templateVar,
                Collections.singletonList(new Translation("en", "Validation hint...")));
        template.addVariable(templateVariable);
        return template;
    }

    private List<RuleDef> buildRequiredValidationsIfRequired(boolean isRequired, String templatePrefix) {
        List<RuleDef> validations = new ArrayList<>();
        if (isRequired) {
            validations = Collections.singletonList(newRequiredValidation(templatePrefix));
        }
        return validations;
    }

    private QuestionBlockDef buildBooleanQuestion(String questionText, String stableId,
                                                  boolean isRequired, boolean bigText) {
        Map<String, String> booleanQuestionTranslations = new HashMap<>();
        booleanQuestionTranslations.put("en", questionText);
        Template booleanQuestionPrompt = createTemplate("q", stableId + "."
                + System.currentTimeMillis(), booleanQuestionTranslations, bigText);
        Map<String, String> yesTranslations = new HashMap<>();
        yesTranslations.put("en", "Yes");
        Map<String, String> noTranslations = new HashMap<>();
        noTranslations.put("en", "No");
        Template trueTemplate = createTemplate("q", stableId + "Yes."
                + System.currentTimeMillis(), yesTranslations, false);
        Template falseTemplate = createTemplate("q", stableId + "No."
                + System.currentTimeMillis(), noTranslations, false);

        return new QuestionBlockDef(new BoolQuestionDef(
                stableId,
                false,
                booleanQuestionPrompt,
                null,
                null,
                buildRequiredValidationsIfRequired(isRequired, stableId),
                trueTemplate,
                falseTemplate,
                false));
    }

    private QuestionBlockDef buildDateQuestion(String questionText, String stableId, boolean bigText,
                                               List<DateFieldType> dateFields, boolean displayCalendar) {
        Map<String, String> dateQuestionTranslations = new HashMap<>();
        dateQuestionTranslations.put("en", questionText);
        Template dateQuestionPrompt = createTemplate("q", stableId + "."
                + "." + System.currentTimeMillis(), dateQuestionTranslations, bigText);

        return new QuestionBlockDef(new DateQuestionDef(stableId,
                                                        false,
                                                        dateQuestionPrompt,
                                                        null,
                                                        null,
                                                        new ArrayList<>(),
                                                        DateRenderMode.TEXT,
                                                        displayCalendar,
                                                        dateFields,
                                                        null,
                                                        false));
    }

    private QuestionBlockDef buildTextQuestion(String questionText, String stableId,
                                               boolean isRequired, boolean bigText) {
        Map<String, String> textQuestionTranslations = new HashMap<>();
        textQuestionTranslations.put("en", questionText);
        Template textQuestionPrompt = createTemplate("q", stableId + "."
                + "." + System.currentTimeMillis(), textQuestionTranslations, bigText);

        return new QuestionBlockDef(new TextQuestionDef(stableId,
                                                            false,
                                                            textQuestionPrompt,
                                                            null,
                                                            null,
                                                            null,
                                                            buildRequiredValidationsIfRequired(isRequired, stableId),
                                                            TextInputType.TEXT,
                                                            false));
    }

    @Test
    @Ignore
    public void insertDemoConsent() {
        FormActivityDef insertedActivity = TransactionWrapper.withTxn(handle -> {
            try {
                String sectionCode = DBUtils.uniqueStandardGuid(handle,
                        SqlConstants.FormSectionTable.TABLE_NAME, SqlConstants.FormSectionTable.SECTION_CODE);

                String atLeast21StableId = "AT_LEAST_21_" + System.currentTimeMillis();
                String agreeToParticipateStableId = "AGREE_TO_PARTICIPATE_" + System.currentTimeMillis();
                String birthDateStableId = "DATE_OF_BIRTH" + System.currentTimeMillis();
                String signatureStableId = "SIGNATURE_" + System.currentTimeMillis();
                String medicalRecordsStableId = "SHARE_MEDICAL_RECORDS_" + System.currentTimeMillis();
                String shareDNAStableId = "SHARE_DNA_" + System.currentTimeMillis();

                Template preambleTemplate = buildConsentPremableTemplate();
                List<FormBlockDef> blocks = new ArrayList<>();
                blocks.add(new ContentBlockDef(preambleTemplate));

                blocks.add(buildBooleanQuestion(I_AM_AT_LEAST_21_YEARS_OF_AGE, atLeast21StableId, true, true));
                blocks.add(buildBooleanQuestion(AGREE_TO_PARTICIPATE, agreeToParticipateStableId, true, true));
                blocks.add(new ContentBlockDef(buildHorzintalDivTemplate(SHARING_MEDICAL_RECORDS,
                        "ShareMR." + System.currentTimeMillis())));
                blocks.add(buildBooleanQuestion(WILLING_TO_SHARE, medicalRecordsStableId, false, false));
                blocks.add(new ContentBlockDef(buildHorzintalDivTemplate(SHARING_GENETIC_INFORMATION,
                        "ShareSeq." + System.currentTimeMillis())));
                blocks.add(buildBooleanQuestion(SHARE_MY_SEQUENCE,
                        shareDNAStableId, false, false));
                blocks.add(buildTextQuestion(SIGNATURE, signatureStableId, true, true));
                QuestionBlockDef dateQuestion = buildDateQuestion(DATE_OF_BIRTH, birthDateStableId, true,
                        Arrays.asList(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR), true);

                blocks.add(dateQuestion);
                List<FormSectionDef> formSections = new ArrayList<>();
                FormSectionDef formSection = new FormSectionDef(sectionCode, blocks);
                formSections.add(formSection);

                List<Translation> activityTranslatedNames = new ArrayList<>();
                activityTranslatedNames.add(new Translation("en", MAIN_CONSENT));
                List<Translation> dashboardNames = new ArrayList<>();
                dashboardNames.add(new Translation("en", MAIN_CONSENT_DASHBOARD_NAME));
                List<Translation> descriptions = new ArrayList<>();
                descriptions.add(new Translation("en", MAIN_CONSENT_DESCRIPTION));
                List<SummaryTranslation> dashboardSummaries = new ArrayList<>();
                dashboardNames.add(new SummaryTranslation("en", MAIN_CONSENT_DASHBOARD_SUMMARY, InstanceStatusType.CREATED));

                // consent condition
                String atLeast21Expression = String.format(PEX_STUDY_ACTIVITY_QUESTION_BASE + ".hasTrue()",
                        atLeast21StableId);
                String agreeToParticipateExpression = String.format(PEX_STUDY_ACTIVITY_QUESTION_BASE + ".hasTrue()",
                        agreeToParticipateStableId);
                String hasSignatureExpression = String.format(PEX_STUDY_ACTIVITY_QUESTION_BASE + ".hasText()",
                        signatureStableId);
                String consentCondition = atLeast21Expression + " && " + agreeToParticipateExpression + " && "
                        + hasSignatureExpression;

                // electives
                String medicalRecordsExpression = String.format(PEX_STUDY_ACTIVITY_QUESTION_BASE + ".hasTrue()",
                        medicalRecordsStableId);
                String shareDNAExpression = String.format(PEX_STUDY_ACTIVITY_QUESTION_BASE + ".hasTrue()",
                        shareDNAStableId);
                List<ConsentElectionDef> elections = new ArrayList<>();
                elections.add(new ConsentElectionDef(medicalRecordsStableId + "."
                        + System.currentTimeMillis(), medicalRecordsExpression));
                elections.add(new ConsentElectionDef(shareDNAStableId + "."
                        + System.currentTimeMillis(), shareDNAExpression));

                Template readonlyHint = Template.html("Please contact your organization regarding the required changes");
                ConsentActivityDef formActivity = new ConsentActivityDef(CONSENT_ACT_CODE, "v1",
                        TestConstants.TEST_STUDY_GUID,
                        1,
                        100,
                        false,
                        activityTranslatedNames,
                        null,
                        dashboardNames,
                        descriptions,
                        dashboardSummaries,
                        readonlyHint,
                        formSections,
                        consentCondition,
                        elections,
                        null,
                        null,
                        null,
                        null,
                        false
                );

                ConsentActivityDao consentActivityDao = handle.attach(ConsentActivityDao.class);
                JdbiUser userDao = handle.attach(JdbiUser.class);
                JdbiRevision revisionDao = handle.attach(JdbiRevision.class);

                long userId = userDao.getUserIdByGuid(TestConstants.TEST_USER_GUID);
                long revisionId = revisionDao.insert(userId, Instant.now().toEpochMilli(), null,
                        "Testing " + getClass().getCanonicalName());

                long startTime = System.currentTimeMillis();
                consentActivityDao.insertActivity(formActivity, revisionId);
                LOG.info("Created activity {} in {}ms", formActivity.getActivityCode(),
                        System.currentTimeMillis() - startTime);

                JdbiActivity jdbiActivity = handle.attach(JdbiActivity.class);
                JdbiBlock jdbiBlock = handle.attach(JdbiBlock.class);
                JdbiFormSection jdbiFormSection = handle.attach(JdbiFormSection.class);

                assertNotNull(formActivity.getActivityId());
                assertThat(formActivity.getActivityCode(), not(isEmptyOrNullString()));
                assertThat(formActivity.getSections(), not(empty()));

                long studyId = handle.attach(JdbiUmbrellaStudy.class).getIdByGuid(TestConstants.TEST_STUDY_GUID).get();
                Optional<ActivityDto> activityDto = jdbiActivity.findActivityByStudyIdAndCode(studyId, formActivity.getActivityCode());
                assertThat(activityDto.get().getActivityCode(), is(formActivity.getActivityCode()));
                assertThat(activityDto.get().getActivityId(), is(formActivity.getActivityId()));

                // run basic section and block content checks
                int sectionNumber = 0;
                for (FormSectionDef insertedSection : formActivity.getSections()) {
                    sectionNumber++;
                    assertThat(insertedSection.getSectionId(), notNullValue());
                    assertThat(insertedSection.getBlocks(), not(empty()));

                    FormSectionDto formSectionDto = jdbiFormSection.findById(insertedSection.getSectionId());
                    assertThat(formSectionDto.getFormSectionId(), is(insertedSection.getSectionId()));
                    assertThat(formSectionDto.getSectionCode(), is(insertedSection.getSectionCode()));

                    assertThat(insertedSection.getBlocks(), not(empty()));
                    int blockNumber = 0;
                    for (FormBlockDef block : insertedSection.getBlocks()) {
                        blockNumber++;
                        assertThat(block.getBlockGuid(), not(isEmptyOrNullString()));
                        assertThat(block.getBlockId(), notNullValue());
                        assertThat(StringUtils.isNotBlank(block.getBlockGuid()), is(true));

                        BlockDto blockDto = jdbiBlock.findById(block.getBlockId());
                        assertThat(blockDto.getGuid(), is(block.getBlockGuid()));
                        assertThat(blockDto.getId(), is(block.getBlockId()));

                        if (sectionNumber == 1 && blockNumber == 1) {
                            verifyTemplate(block, PREAMBLE_TEXT);
                        } else if (sectionNumber == 1 && blockNumber == 2) {
                            verifyQuestion(block, atLeast21StableId, I_AM_AT_LEAST_21_YEARS_OF_AGE);
                        } else if (sectionNumber == 1 && blockNumber == 3) {
                            verifyQuestion(block, agreeToParticipateStableId, AGREE_TO_PARTICIPATE);
                        } else if (sectionNumber == 1 && blockNumber == 4) {
                            verifyTemplate(block, SHARING_MEDICAL_RECORDS);
                        } else if (sectionNumber == 1 && blockNumber == 5) {
                            verifyQuestion(block, medicalRecordsStableId, WILLING_TO_SHARE);
                        } else if (sectionNumber == 1 && blockNumber == 6) {
                            verifyTemplate(block, SHARING_GENETIC_INFORMATION);
                        } else if (sectionNumber == 1 && blockNumber == 7) {
                            verifyQuestion(block, shareDNAStableId, SHARE_MY_SEQUENCE);
                        } else if (sectionNumber == 1 && blockNumber == 8) {
                            verifyQuestion(block, signatureStableId, SIGNATURE);
                        } else if (sectionNumber == 1 && blockNumber == 9) {
                            verifyQuestion(block, birthDateStableId, DATE_OF_BIRTH);
                        } else {
                            Assert.fail("Unknown block " + blockNumber);
                        }
                    }
                }

                LOG.info(new GsonBuilder().setPrettyPrinting().create().toJson(formActivity));
                return formActivity;
            } finally {
                handle.rollback();
            }
        });

    }

    private void verifyTemplate(FormBlockDef block, String textContents) {
        ContentBlockDef contentDef = (ContentBlockDef) block;
        assertThat(contentDef.getBodyTemplate().getVariables(), hasSize(1));
        assertThat(contentDef.getBodyTemplate().getVariables().iterator().next().getTranslations(), hasSize(1));

        String templateText = contentDef.getBodyTemplate().getVariables().iterator().next()
                .getTranslations().iterator().next().getText();
        assertThat(templateText, is(textContents));
    }

    private void verifyQuestion(FormBlockDef block, String stableId, String promptText) {
        QuestionDef question = ((QuestionBlockDef) block).getQuestion();
        Template template = question.getPromptTemplate();
        assertThat(question.getStableId(), is(stableId));
        assertThat(question.isRestricted(), is(false));
        assertThat(template.getVariables(), hasSize(1));
        assertThat(template.getVariables().iterator().next().getTranslations(), hasSize(1));
        String englishText = template.getVariables().iterator()
                .next().getTranslations().iterator().next().getText();
        assertThat(englishText, is(promptText));
    }

    private RequiredRuleDef newRequiredValidation(String hintTemplatePrefix) {
        return new RequiredRuleDef(buildValidationHintTemplate(hintTemplatePrefix));
    }

}

package org.broadinstitute.ddp.script.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ConsentActivityDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to construct and create demo activities for the second test study on `v1_dev`.
 * This file should not be picked up by unit test suites.
 */
@Ignore
public class SecondStudyDemoActivitiesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(SecondStudyDemoActivitiesScript.class);

    private static final String USER_GUID = TestConstants.TEST_USER_GUID;
    private static final String STUDY_GUID = TestConstants.SECOND_STUDY_GUID;

    private static final String CHOOSE_OPTION_HINT = "Please choose one of the above options";
    private static final String REQUIRED_NOTE = "<p><em>* Required field</em></p>";

    private static final String PREQUAL_ACT_CODE = "Z8EEFS6BFF";
    private static final String PREQUAL_ACT_NAME = "Join the movement: tell us about yourself";
    private static final String PREQUAL_ACT_DASHBOARD_NAME = "About you";
    private static final String PREQUAL_ACT_DESCRIPTION = "This form allows to understand if you're qualified for the study";
    private static final String PREQUAL_TITLE = "<h1>" + PREQUAL_ACT_NAME + "</h1>";
    private static final String PREQUAL_INFO = "<br/>"
            + "<p>Complete the form below to tell us about yourself and your cancer. Our goal"
            + " is to perform many different studies within the metastatic/advanced prostate cancer community."
            + " Allowing us to know a little bit about your experience will help us conduct our current projects and"
            + " also design future studies based on what we learn. We are asking all patients with metastatic and/or"
            + " advanced prostate cancer to say \"Count Me In\" and fill out the form so that we can use the"
            + " information you provide to plan our next studies.</p>"
            + "<br/>";
    private static final String PREQUAL_DIAG_YES = "I have been diagnosed with metastatic"
            + " and/or advanced prostate cancer."
            + "<br/>"
            + " I'm willing to answer additional questions about myself and my experience"
            + "<br/>"
            + " with metastatic and/or advanced prostate cancer.";
    private static final String PREQUAL_DIAG_NO = "I haven't been diagnosed with metastatic"
            + " and/or advanced prostate cancer,"
            + "<br/>"
            + " but I want to stay informed about the Metastatic Prostate Cancer Project.";

    private static final String CONSENT_ACT_CODE = "RDEFOAMTPO";
    private static final String CONSENT_ACT_NAME = "Research Consent Form";
    private static final String CONSENT_ACT_DASHBOARD_NAME = "Consent Form";
    private static final String CONSENT_ACT_DESCRIPTION = "By filling a Consent Form you agree to participate in the study"
            + " and accept its terms and conditions";
    private static final String CONSENT_ACT_DASHBOARD_SUMMARY = "This form is required to get the participant consent";
    private static final String PREQUAL_ACT_DASHBOARD_SUMMARY =
            "This form is required to figure out if the user qualifies for the study";
    private static final String CONSENT_TITLE = "<h1>" + CONSENT_ACT_NAME + "</h1>";
    private static final String CONSENT_KEY_POINTS = "<h2>Key Points</h2>"
            + "<p><strong>\"The Metastatic Prostate Cancer Project\"</strong> is a patient-driven"
            + " movement that empowers prostate cancer patients to directly transform research and treatment of"
            + " disease by sharing copies of their medical records and tissue and/or blood samples with researchers"
            + " in order to accelerate the pace of discovery. Because we are enrolling participants across the country"
            + " regardless of where they are being treated, this study will allow many more patients to contribute to"
            + " research than has previously been possible.</p>";
    private static final String CONSENT_INTRO = "<h2>Introduction</h2>"
            + "<p>You are being invited to participate in a research study that will collect and analyze samples"
            + " and health information of patients with prostate cancer. This study will help doctors and researchers"
            + " better understand why prostate cancer occurs and develop ways to better treat and prevent it."
            + "</p>"
            + "<p>Cancers occur when the molecules that control normal cell growth (genes and proteins) are altered."
            + " Changes in the genes of tumor cells and normal tissues are called \"alterations.\" Several alterations"
            + " that occur in certain types of cancers have already been identified and have led to the development of"
            + " new drugs that specifically target those alterations. However, the vast majority of tumors from"
            + " patients have not been studied, which means there is a tremendous amount of information still left to"
            + " be discovered. Our goal is to discover more alterations, and to better understand those that have been"
            + " previously described. We think this could lead to the development of additional therapies and cures."
            + "</p>"
            + "<p>Genes are composed of DNA \"letters,\" which contain the instructions that tell the cells in our"
            + " bodies how to grow and work. We would like to use your DNA to look for alterations in cancer cell"
            + " genes using a technology called \"sequencing.\""
            + "</p>"
            + "<p>Gene sequencing is a way of reading the DNA to identify alterations in genes that may contribute"
            + " to the behavior of cells. Some changes in genes occur only in cancer cells. Others occur in normal"
            + " cells as well, in the genes that may have been passed from parent to child. This research study will"
            + " examine both kinds of genes."
            + "</p>"
            + "<p>You are being asked to participate in the study because you have prostate cancer. Other than"
            + " providing saliva samples and, if you elect to, blood sample (1 tube or 2 teaspoons), participating"
            + " in the study involves no additional tests or procedures."
            + " The decision to participate is yours. We encourage you to ask questions about the study"
            + " now or in the future."
            + "</p>";
    private static final String CONSENT_ELECTION_INTRO = "<h2>Documentation of Consent</h2>"
            + "<h3>This is what I, the patient, agree to:</h3>";
    private static final String CONSENT_BLOOD_SAMPLE_PROMPT = "<p>You can work with me to arrange a sample of blood"
            + " to be drawn at my physician’s office, local clinic, or nearby lab facility. *</p>";
    private static final String CONSENT_STORE_SAMPLE_PROMPT = "<p>You can request my stored tissue samples from my"
            + " physicians and the hospitals and other places where I received my care, perform (or collaborate with"
            + " others to perform) gene tests on the samples, and store the samples until this research study is"
            + " complete. *</p>";
    private static final String CONSENT_AGREEMENT = "<h3>In addition, I agree to all of the following:</h3>"
            + "<ul>"
            + "<li><p>You can request my medical records from my physicians and the hospitals and other places where"
            + " I received and/or continue to receive my treatment and link results of the gene tests you perform on"
            + " my saliva and, if I elect on this form, blood and tissue samples with my medical information from my"
            + " medical records."
            + "</p></li>"
            + "<li><p>You can analyze a saliva sample that I will send you, link the results to my medical information"
            + " and other specimens, and store the specimen to use it for future research."
            + "</p></li>"
            + "<li><p>You can perform (or collaborate with others to perform) gene tests on the blood and saliva"
            + " samples that I will send you and store the samples until this research study is complete."
            + "</p></li>"
            + "<li><p>You can use the results of the gene tests and my medical information for future research studies,"
            + " including studies that have not yet been designed, studies for diseases other than cancer, and/or"
            + " studies that may be for commercial purposes."
            + "</p></li>"
            + "<li><p>You can share the results of the gene tests and my medical information with central data banks"
            + " (e.g., the NIH) and with other qualified researchers in a manner that does not include my name, social"
            + " security number, or any other information that could be used to readily identify me, to be used by"
            + " other qualified researchers to perform future research studies, including studies that have not yet"
            + " been designed, studies for diseases other than cancer, and studies that may be for commercial purposes."
            + "</p></li>"
            + "</ul>";
    private static final String CONSENT_SIGNATURE_AGREEMENT = "<h3>My full name below indicates:</h3>"
            + "<ul>"
            + "<li><p>I have had enough time to read the consent and think about agreeing to"
            + " participate in this study.</p></li>"
            + "<li><p>I have had all of my questions answered to my satisfaction.</p></li>"
            + "<li><p>I am willing to participate in this research study.</p></li>"
            + "<li><p>I have been told that my participation is voluntary and if I decide not to participate"
            + " it will have no impact on my medical care.</p></li>"
            + "<li><p>I have been told that if I decide to participate now, I can decide to stop being in the"
            + " study at any time.</p></li>"
            + "<li><p>I acknowledge that a copy of the signed consent form will be sent to my email address.</p></li>"
            + "</ul>";

    private static final String CONSENT_EXPR_FMT = "user"
            + ".studies[\"" + STUDY_GUID + "\"].forms[\"" + CONSENT_ACT_CODE + "\"]"
            + ".questions[\"%s\"].answers.hasText()";
    private static final String ELECTION_EXPR_FMT = "user"
            + ".studies[\"" + STUDY_GUID + "\"].forms[\"" + CONSENT_ACT_CODE + "\"]"
            + ".questions[\"%s\"].answers.hasTrue()";

    private static Gson gson;
    private static String sidPrequalFname;
    private static String sidPrequalLname;
    private static String sidPrequalDiagnosed;
    private static String sidConsentBlood;
    private static String sidConsentStore;
    private static String sidConsentSignature;
    private static String sidConsentBirth;
    private static String sidElectionBlood;
    private static String sidElectionStore;
    private static String consentExpr;
    private static String electionBloodExpr;
    private static String electionStoreExpr;

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().setPrettyPrinting().create();
        long timestamp = Instant.now().getEpochSecond();

        sidPrequalFname = "S2_PREQUAL_FNAME_" + timestamp;
        sidPrequalLname = "S2_PREQUAL_LNAME_" + timestamp;
        sidPrequalDiagnosed = "S2_PREQUAL_DIAGNOSED_" + timestamp;
        sidConsentBlood = "S2_CONSENT_BLOOD_SAMPLE_" + timestamp;
        sidConsentStore = "S2_CONSENT_STORE_SAMPLE_" + timestamp;
        sidConsentSignature = "S2_CONSENT_SIGNATURE_" + timestamp;
        sidConsentBirth = "S2_CONSENT_BIRTH_" + timestamp;
        sidElectionBlood = "S2_ELECTION_BLOOD_SAMPLE_" + timestamp;
        sidElectionStore = "S2_ELECTION_STORE_SAMPLE_" + timestamp;

        consentExpr = String.format(CONSENT_EXPR_FMT, sidConsentSignature);
        electionBloodExpr = String.format(ELECTION_EXPR_FMT, sidConsentBlood);
        electionStoreExpr = String.format(ELECTION_EXPR_FMT, sidConsentStore);
    }

    @Test
    @Ignore
    public void insertPrequal() {
        // template block: title
        Template titleTmpl = buildTemplate("title", PREQUAL_TITLE);
        ContentBlockDef titleBlock = new ContentBlockDef(titleTmpl);

        // template block: info blurb
        Template infoTmpl = buildTemplate("info", PREQUAL_INFO);
        ContentBlockDef infoBlock = new ContentBlockDef(infoTmpl);

        // text question: first name
        Template firstNamePrompt = buildTemplate("fname", "First Name *");
        Template firstNameReqHint = buildTemplate("fname_req", "First Name is required");
        Template firstNameLenHint = buildTemplate("fname_len", "First Name cannot be blank");
        List<RuleDef> firstNameRules = Arrays.asList(
                new RequiredRuleDef(firstNameReqHint),
                new LengthRuleDef(firstNameLenHint, 1, null));
        QuestionBlockDef firstNameQuestion = new QuestionBlockDef(new TextQuestionDef(sidPrequalFname,
                                                                                        true,
                                                                                        firstNamePrompt,
                                                                                        null,
                                                                                        null,
                                                                                        null,
                                                                                        firstNameRules,
                                                                                        TextInputType.TEXT,
                                                                                        true));

        // text question: last name
        Template lastNamePrompt = buildTemplate("lname", "Last Name *");
        Template lastNameReqHint = buildTemplate("lname_req", "Last Name is required");
        Template lastNameLenHint = buildTemplate("lname_len", "Last Name cannot be blank");
        List<RuleDef> lastNameRules = Arrays.asList(
                new RequiredRuleDef(lastNameReqHint),
                new LengthRuleDef(lastNameLenHint, 1, null));
        QuestionBlockDef lastNameQuestion = new QuestionBlockDef(new TextQuestionDef(sidPrequalLname,
                                                                                        true,
                                                                                        lastNamePrompt,
                                                                                        null,
                                                                                        null,
                                                                                        null,
                                                                                        lastNameRules,
                                                                                        TextInputType.TEXT,
                                                                                        true));

        // bool question: diagnosed
        Template diagPrompt = new Template(TemplateType.HTML, null, "<!-- no prompt -->");
        Template diagYesPrompt = buildTemplate("diag_yes", PREQUAL_DIAG_YES);
        Template diagNoPrompt = buildTemplate("diag_no", PREQUAL_DIAG_NO);
        Template diagReqHint = buildTemplate("diag_req", CHOOSE_OPTION_HINT);
        List<RuleDef> diagRules = Collections.singletonList(new RequiredRuleDef(diagReqHint));
        QuestionBlockDef diagQuestion = new QuestionBlockDef(new BoolQuestionDef(sidPrequalDiagnosed,
                                                                                    false,
                                                                                    diagPrompt,
                                                                                    null,
                                                                                    null,
                                                                                    diagRules,
                                                                                    diagYesPrompt,
                                                                                    diagNoPrompt,
                                                                                    true));

        // template block: required footnote
        Template noteTmpl = buildTemplate("req_note", REQUIRED_NOTE);
        ContentBlockDef noteBlock = new ContentBlockDef(noteTmpl);

        List<FormSectionDef> sections = Arrays.asList(
                new FormSectionDef(null, Arrays.asList(titleBlock, infoBlock)),
                new FormSectionDef(null, Arrays.asList(firstNameQuestion, lastNameQuestion, diagQuestion, noteBlock)));
        List<Translation> names = Collections.singletonList(new Translation("en", PREQUAL_ACT_NAME));
        List<Translation> dashboardNames = Collections.singletonList(new Translation("en", PREQUAL_ACT_DASHBOARD_NAME));
        List<Translation> descriptions = Collections.singletonList(new Translation("en", PREQUAL_ACT_DESCRIPTION));
        List<SummaryTranslation> dashboardSummaries = Collections.singletonList(
                new SummaryTranslation("en", PREQUAL_ACT_DASHBOARD_SUMMARY, InstanceStatusType.CREATED)
        );

        int order = 1;
        int instancesAllowed = 1;
        Template readonlyHint = Template.html("Please contact your organization regarding the required changes");
        FormActivityDef prequal = new FormActivityDef(
                FormType.PREQUALIFIER, PREQUAL_ACT_CODE, "v1", STUDY_GUID,
                instancesAllowed, order, false, names, null, dashboardNames,
                descriptions, dashboardSummaries, readonlyHint, null, sections, null, null, null, false
        );

        TransactionWrapper.useTxn(handle -> {
            long millis = Instant.now().toEpochMilli();
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "Add prequal for second study");

            FormActivityDao formActivityDao = handle.attach(FormActivityDao.class);
            formActivityDao.insertActivity(prequal, revId);
            assertNotNull(prequal.getActivityId());

            // current app workflow does not create a prequal instance so we mark this auto-instantiated
            int numUpdated = handle.attach(JdbiActivity.class)
                    .updateAutoInstantiateById(prequal.getActivityId(), true);
            assertEquals("failed to make prequal auto-instantiated", 1, numUpdated);

            LOG.info("Created prequal with activity id={} json=\n{}", prequal.getActivityId(), gson.toJson(prequal));

            handle.rollback();      // <- comment out to persist
        });
    }

    @Test
    @Ignore
    public void insertConsent() {
        // template block: title
        Template titleTmpl = buildTemplate("title", CONSENT_TITLE);
        ContentBlockDef titleBlock = new ContentBlockDef(titleTmpl);

        // template block: key points
        Template keyTmpl = buildTemplate("key_points", CONSENT_KEY_POINTS);
        ContentBlockDef keyBlock = new ContentBlockDef(keyTmpl);
        FormSectionDef keySection = new FormSectionDef(null, Arrays.asList(titleBlock, keyBlock));

        // template block: intro
        Template introTmpl = buildTemplate("intro", CONSENT_INTRO);
        ContentBlockDef introBlock = new ContentBlockDef(introTmpl);
        FormSectionDef introSection = new FormSectionDef(null, Collections.singletonList(introBlock));

        // template block: election intro
        Template electionIntroTmpl = buildTemplate("election_intro", CONSENT_ELECTION_INTRO);
        ContentBlockDef electionIntroBlock = new ContentBlockDef(electionIntroTmpl);

        // bool question: arrange blood sample election
        Template bloodSamplePrompt = buildTemplate("blood_sample", CONSENT_BLOOD_SAMPLE_PROMPT);
        Template bloodSampleYesPrompt = buildTemplate("blood_sample_yes", "Yes");
        Template bloodSampleNoPrompt = buildTemplate("blood_sample_no", "No");
        Template bloodSampleReqHint = buildTemplate("blood_sample_req", CHOOSE_OPTION_HINT);
        List<RuleDef> bloodSampleRules = Collections.singletonList(new RequiredRuleDef(bloodSampleReqHint));
        QuestionBlockDef bloodSampleQuestion = new QuestionBlockDef(new BoolQuestionDef(sidConsentBlood,
                                                                                        false,
                                                                                        bloodSamplePrompt,
                                                                                        null,
                                                                                        null,
                                                                                        bloodSampleRules,
                                                                                        bloodSampleYesPrompt,
                                                                                        bloodSampleNoPrompt,
                                                                                        true));

        // bool question: request stored samples election
        Template storeSamplePrompt = buildTemplate("store_sample", CONSENT_STORE_SAMPLE_PROMPT);
        Template storeSampleYesPrompt = buildTemplate("store_sample_yes", "Yes");
        Template storeSampleNoPrompt = buildTemplate("store_sample_no", "No");
        Template storeSampleReqHint = buildTemplate("store_sample_req", CHOOSE_OPTION_HINT);
        List<RuleDef> storeSampleRules = Collections.singletonList(new RequiredRuleDef(storeSampleReqHint));
        QuestionBlockDef storeSampleQuestion = new QuestionBlockDef(new BoolQuestionDef(sidConsentStore,
                                                                                        false,
                                                                                        storeSamplePrompt,
                                                                                        null,
                                                                                        null,
                                                                                        storeSampleRules,
                                                                                        storeSampleYesPrompt,
                                                                                        storeSampleNoPrompt,
                                                                                        true));

        // template block: agreement
        Template agreeTmpl = buildTemplate("agreement", CONSENT_AGREEMENT);
        ContentBlockDef agreeBlock = new ContentBlockDef(agreeTmpl);

        // template block: signature agreement
        Template signAgreeTmpl = buildTemplate("signature_agreement", CONSENT_SIGNATURE_AGREEMENT);
        ContentBlockDef signAgreeBlock = new ContentBlockDef(signAgreeTmpl);

        // text question: full name signature
        Template signaturePrompt = buildTemplate("signature", "Your Full Name *");
        Template signatureReqHint = buildTemplate("signature_req", "Full Name is required");
        Template signLengthHint = buildTemplate("signature_len", "Full Name cannot be blank");
        List<RuleDef> signatureRules = Arrays.asList(
                new RequiredRuleDef(signatureReqHint),
                new LengthRuleDef(signLengthHint, 1, null));
        QuestionBlockDef signatureQuestion = new QuestionBlockDef(new TextQuestionDef(sidConsentSignature,
                                                                                        true,
                                                                                        signaturePrompt,
                                                                                        null,
                                                                                        null,
                                                                                        null,
                                                                                        signatureRules,
                                                                                        TextInputType.TEXT,
                                                                                        true));

        // date question: birth date
        Template birthPrompt = buildTemplate("birth_date", "Date of birth *");
        Template birthReqHint = buildTemplate("birth_req", "Please enter date of birth in MM DD YYYY format");
        Template monthReqHint = buildTemplate("month_req", "Please enter birth month in MM format");
        Template dayReqHint = buildTemplate("day_req", "Please enter birth day in DD format");
        Template yearReqHint = buildTemplate("year_req", "Please enter birth year in YYYY format");
        List<RuleDef> birthRules = Arrays.asList(
                new RequiredRuleDef(birthReqHint),
                new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, monthReqHint),
                new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, dayReqHint),
                new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, yearReqHint));
        List<DateFieldType> birthFields = Arrays.asList(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR);
        QuestionBlockDef birthQuestion = new QuestionBlockDef(new DateQuestionDef(sidConsentBirth,
                                                                                    true,
                                                                                    birthPrompt,
                                                                                    null,
                                                                                    null,
                                                                                    birthRules,
                                                                                    DateRenderMode.TEXT,
                                                                                    false, birthFields, null, true));

        // template block: required footnote
        Template reqNoteTmpl = buildTemplate("req_note", REQUIRED_NOTE);
        ContentBlockDef reqNoteBlock = new ContentBlockDef(reqNoteTmpl);

        FormSectionDef signSection = new FormSectionDef(null, Arrays.asList(
                electionIntroBlock, bloodSampleQuestion, storeSampleQuestion,
                agreeBlock, signAgreeBlock,
                signatureQuestion, birthQuestion,
                reqNoteBlock));

        List<FormSectionDef> sections = Arrays.asList(keySection, introSection, signSection);
        List<Translation> names = Collections.singletonList(new Translation("en", CONSENT_ACT_NAME));
        List<Translation> dashboardNames = Collections.singletonList(new Translation("en", CONSENT_ACT_DASHBOARD_NAME));
        List<Translation> descriptions = Collections.singletonList(new Translation("en", CONSENT_ACT_DESCRIPTION));
        List<SummaryTranslation> dashboardSummaries = Collections.singletonList(
                new SummaryTranslation("en", CONSENT_ACT_DASHBOARD_SUMMARY, InstanceStatusType.CREATED)
        );

        List<ConsentElectionDef> elections = Arrays.asList(
                new ConsentElectionDef(sidElectionBlood, electionBloodExpr),
                new ConsentElectionDef(sidElectionStore, electionStoreExpr));

        int order = 10;
        int instancesAllowed = 1;
        Template readonlyHint = Template.html("Please contact your organization regarding the required changes");
        ConsentActivityDef consent = new ConsentActivityDef(
                CONSENT_ACT_CODE, "v1", STUDY_GUID, instancesAllowed, order,
                false, names, null, dashboardNames, descriptions, dashboardSummaries,
                readonlyHint, sections, consentExpr, elections, null, null, null, null, false
        );

        TransactionWrapper.useTxn(handle -> {
            long millis = Instant.now().toEpochMilli();
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "Add consent for second study");

            ConsentActivityDao consentActivityDao = handle.attach(ConsentActivityDao.class);
            consentActivityDao.insertActivity(consent, revId);
            assertNotNull(consent.getActivityId());

            LOG.info("Created consent with activity id={} json=\n{}", consent.getActivityId(), gson.toJson(consent));

            handle.rollback();      // <- comment out to persist
        });
    }

    /**
     * Build a html-based template with a single english variable translation.
     */
    public static Template buildTemplate(String varName, String varText) {
        Translation enTrans = new Translation("en", varText);
        TemplateVariable var = new TemplateVariable(varName, Collections.singletonList(enTrans));
        String templateText = Template.VELOCITY_VAR_PREFIX + varName;
        Template tmpl = new Template(TemplateType.HTML, null, templateText);
        tmpl.addVariable(var);
        return tmpl;
    }
}

package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.model.activity.types.ActivityMappingType.DATE_OF_DIAGNOSIS;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.ANGIO_USER_GUID;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.READONLY_CONTACT_INFO_HTML;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateBodyLocationOptions;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateHtmlTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateOptions;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateOptionsWithDetails;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateQuestionPrompt;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateTextTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateYNDKOptions;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivityMapping;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class AngioAboutYouActivityCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioAboutYouActivityCreationScript.class);

    private static final String USER_GUID = ANGIO_USER_GUID;
    private static final String STUDY_GUID = AngioStudyCreationScript.ANGIO_STUDY_GUID;

    private static final String NUANCE = ""; //Adds a fudge factor, like '_123'. Useful for testing, leave empty for the real thing.
    public static final String ACTIVITY_CODE = "ANGIOABOUTYOU" + NUANCE;

    private static String DIAGNOSIS_DATE_SID = "DIAGNOSIS_DATE" + NUANCE;

    private static final String PEX_HAS_OPTION_FORMAT = "user.studies[\"" + STUDY_GUID + "\"]"
            + ".forms[\"" + ACTIVITY_CODE + "\"].questions[\"%s\"].answers.hasOption(\"%s\")";

    @Test
    public void insertAboutYouActivity() {
        List<QuestionBlockDef> deprecated = buildDeprecatedBlocks();

        FormSectionDef body = buildBodySection();
        body.getBlocks().addAll(deprecated);

        FormActivityDef aboutYou = FormActivityDef.formBuilder(FormType.PREQUALIFIER, ACTIVITY_CODE, "v1", STUDY_GUID)
                .setListStyleHint(ListStyleHint.NUMBER)
                .setMaxInstancesPerUser(1)
                .setWriteOnce(false)
                .setDisplayOrder(1)
                .addName(new Translation("en", "Join the movement: tell us about yourself"))
                .addDashboardName(new Translation("en", "Initial Enrollment Survey"))
                .setIntroduction(buildIntroSection())
                .addSection(body)
                .setClosing(buildClosingSection())
                .setReadonlyHintTemplate(buildReadonlyHintTemplate())
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "Completing the Initial Survey will tell us about your experiences with angiosarcoma",
                        InstanceStatusType.CREATED))
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "Submitting the Initial Survey will take you to the Research Consent Form to enroll in "
                                + "the Angiosarcoma Project",
                        InstanceStatusType.IN_PROGRESS))
                .addSummary(new SummaryTranslation(
                        LanguageConstants.EN_LANGUAGE_CODE,
                        "All set - your next step is the Research Consent Form",
                        InstanceStatusType.COMPLETE))
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            ActivityDao activityDao = handle.attach(ActivityDao.class);
            Gson gson = GsonUtil.standardBuilder().setPrettyPrinting().create();

            long startMillis = ACTIVITY_TIMESTAMP_ANCHOR;
            RevisionMetadata meta = new RevisionMetadata(startMillis, userId, "Creating angio about-you activity");
            activityDao.insertActivity(aboutYou, meta);
            assertNotNull(aboutYou.getActivityId());
            LOG.info("Created angio about-you activity code={} id={} version={} json=\n{}",
                    aboutYou.getActivityCode(), aboutYou.getActivityId(), aboutYou.getVersionTag(), gson.toJson(aboutYou));

            handle.attach(JdbiActivityMapping.class).insert(STUDY_GUID,
                    DATE_OF_DIAGNOSIS.name(),
                    aboutYou.getActivityId(),
                    DIAGNOSIS_DATE_SID);
        });
    }

    private Template buildReadonlyHintTemplate() {
        return generateHtmlTemplate("angio_about_you_readonly_hint", "<span class=\"ddp-block-title-bold\">"
                + "Thank you for submitting your survey. " + READONLY_CONTACT_INFO_HTML + "</span>");
    }

    private FormSectionDef buildIntroSection() {
        String thankYouText = "Thank you for providing your contact information."
                + " Please help us understand more about your angiosarcoma by answering the questions below.";
        String autoSaveText = "As you fill out the questions below,"
                + " your answers will be automatically saved. If you’ve previously entered information here and want to"
                + " pick up where you left off, please use the link we sent you via email to return to this page.";
        String emailDeleteText = "If you would like your information deleted from our database,"
                + " please let us know by emailing <a href=\"mailto:info@ascproject.org\" class=\"Link\">info@ascproject.org</a>"
                + " and we will remove your name and email address and the answers to any questions you may have answered.";

        Template bodyTmpl = Template.html("<div class=\"PageContent-box\">"
                + "<p class=\"PageContent-box-text\">$intro_thank_you</p>"
                + "<p class=\"PageContent-box-text\">$intro_auto_save</p>"
                + "<p class=\"PageContent-box-text\">$intro_email_for_delete</p>"
                + "</div>");
        bodyTmpl.addVariable(TemplateVariable.single("intro_thank_you", "en", thankYouText));
        bodyTmpl.addVariable(TemplateVariable.single("intro_auto_save", "en", autoSaveText));
        bodyTmpl.addVariable(TemplateVariable.single("intro_email_for_delete", "en", emailDeleteText));

        ContentBlockDef intro = new ContentBlockDef(null, bodyTmpl);
        return new FormSectionDef("angio_about_you_intro" + NUANCE, Collections.singletonList(intro));
    }

    private FormSectionDef buildClosingSection() {
        String acknowledgeText = "I understand that the information I entered here will be stored in a secure database"
                + " and may be used to match me to one or more research studies conducted by the Angiosarcoma Project."
                + " If the information that I entered matches a study being conducted by the Angiosarcoma Project,"
                + " either now or in the future, I agree to be contacted about possibly participating."
                + " I understand that if I would like my information deleted from the database, now or in the future,"
                + " I can email <a href=\"mailto:info@ascproject.org\" class=\"Link\">info@ascproject.org</a>"
                + " and my information will be removed from the database.";

        Template bodyTmpl = Template.html("<p class=\"PageContent-closing-text\">$closing_acknowledgement</p>");
        bodyTmpl.addVariable(TemplateVariable.single("closing_acknowledgement", "en", acknowledgeText));

        ContentBlockDef closing = new ContentBlockDef(null, bodyTmpl);
        return new FormSectionDef("angio_about_you_closing" + NUANCE, Collections.singletonList(closing));
    }

    private FormSectionDef buildBodySection() {
        return new FormSectionDef("angio_about_you_body" + NUANCE, new ArrayList<>(Arrays.asList(
                buildInstructionBlock(),

                buildDiagnosisDateBlock(),
                buildDiagnosisLocBlock(),
                buildEverLocationBlock(),
                buildCurrentLocationBlock(),

                buildInterludeBlock(),

                buildSurgeryBlock(),
                buildRadiationBlock(),
                buildTreatmentBlock(),
                buildCurrentlyTreatedBlock(),
                buildOtherCancerBlock(),
                buildOtherCancerRadiationBlock(),

                buildReferralBlock(),
                buildExperienceBlock(),
                buildHispanicBlock(),
                buildRaceBlock(),
                buildBirthYearBlock(),
                buildCountryBlock(),
                buildPostalCodeBlock()
        )));
    }

    private ContentBlockDef buildInstructionBlock() {
        String titleText = "About you";
        String bodyText = "Please fill out as much as you can. All questions are optional."
                + " You can return at any time with the link sent to you by email.";

        Template titleTmpl = Template.html("<h1 class=\"PageContent-title\">$instruction_title</h1>");
        titleTmpl.addVariable(TemplateVariable.single("instruction_title", "en", titleText));

        Template bodyTmpl = Template.html("<p class=\"PageContent-text\">$instruction_body</p>");
        bodyTmpl.addVariable(TemplateVariable.single("instruction_body", "en", bodyText));

        return new ContentBlockDef(titleTmpl, bodyTmpl);
    }

    private QuestionBlockDef buildDiagnosisDateBlock() {
        Template prompt = generateQuestionPrompt(DIAGNOSIS_DATE_SID, "When were you first diagnosed with angiosarcoma?");

        return new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.PICKLIST, DIAGNOSIS_DATE_SID, prompt)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(true, 0, 119, null, null))
                .build());
    }

    private QuestionBlockDef buildDiagnosisLocBlock() {
        String sid = "DIAGNOSIS_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "When you were"
                + " <span class=\"Underline\">first</span> diagnosed with angiosarcoma,"
                + " where in your body was it found (select all that apply)?");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateBodyLocOptions(sid))
                .build());
    }

    private QuestionBlockDef buildEverLocationBlock() {
        String sid = "EVER_LOCATION" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Please select all of the places in your body"
                + " that you have <span class=\"Underline\">ever</span> had angiosarcoma (select all that apply).");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateBodyLocOptions(sid))
                .build());
    }

    private QuestionBlockDef buildCurrentLocationBlock() {
        String sid = "CURRENT_LOCATION" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Please select all of the places in your body"
                + " where you currently have angiosarcoma (select all that apply). If you don’t have evidence of disease,"
                + " please select \"No Evidence of Disease (NED)\".");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateBodyLocOptionsWithNoEvidence(sid))
                .build());
    }

    private ContentBlockDef buildInterludeBlock() {
        String bodyText = "To help us understand the full scope of how your angiosarcoma was treated,"
                + " the following questions will ask you separately about surgery, radiation, and any medications, drugs,"
                + " or chemotherapies you may have received for angiosarcoma.";
        Template bodyTmpl = Template.html("<div class=\"PageContent-infobox\">"
                + "<p class=\"PageContent-text Color--black\">$interlude</p>"
                + "</div>");
        bodyTmpl.addVariable(TemplateVariable.single("interlude", "en", bodyText));
        return new ContentBlockDef(null, bodyTmpl);
    }

    private ConditionalBlockDef buildSurgeryBlock() {
        String surgerySid = "SURGERY" + NUANCE;
        Template surgeryPrompt = generateQuestionPrompt(surgerySid, "Have you had surgery to remove angiosarcoma?");
        QuestionDef surgery = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, surgerySid, surgeryPrompt)
                .addOptions(generateYNDKOptions(surgerySid))
                .build();

        String cleanMarginsSid = "SURGERY_CLEAN_MARGINS" + NUANCE;
        Template cleanMarginsPrompt = generateQuestionPrompt(cleanMarginsSid, "If so, did the surgery"
                + " remove all known cancer tissue (also known as \"clean margins\")?");
        QuestionBlockDef cleanMargins = new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, cleanMarginsSid, cleanMarginsPrompt)
                .addOptions(generateYNDKOptions(cleanMarginsSid))
                .build());
        cleanMargins.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, surgerySid, "YES"));

        ConditionalBlockDef surgeryBlock = new ConditionalBlockDef(surgery);
        surgeryBlock.addNestedBlock(cleanMargins);
        return surgeryBlock;
    }

    private ConditionalBlockDef buildRadiationBlock() {
        String radiationSid = "RADIATION" + NUANCE;
        Template radPrompt = generateQuestionPrompt(radiationSid, "Have you had radiation as a treatment for angiosarcoma?"
                + " If you had radiation for other cancers, we will ask you about that later.");
        QuestionDef radiation = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, radiationSid, radPrompt)
                .addOptions(generateYNDKOptions(radiationSid))
                .build();

        String radiationSurgerySid = "RADIATION_SURGERY" + NUANCE;
        Template radiationSurgeryPrompt = generateQuestionPrompt(radiationSurgerySid, "Was your radiation before or after surgery?");
        QuestionBlockDef radiationSurgery = new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, radiationSurgerySid, radiationSurgeryPrompt)
                .addOptions(generateRelativeTimeOptions(radiationSurgerySid))
                .build());
        radiationSurgery.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, radiationSid, "YES"));

        ConditionalBlockDef radiationBlock = new ConditionalBlockDef(radiation);
        radiationBlock.addNestedBlock(radiationSurgery);
        return radiationBlock;
    }

    private QuestionBlockDef buildTreatmentBlock() {
        String sid = "ALL_TREATMENTS" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Please list the medications, drugs,"
                + " and chemotherapies you have been prescribed specifically for the treatment of angiosarcoma."
                + " It's okay if there are treatments you don't remember.");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt).build());
    }

    private ConditionalBlockDef buildCurrentlyTreatedBlock() {
        String currentlyTreatedSid = "CURRENTLY_TREATED" + NUANCE;
        Template currentlyTreatedPrompt = generateQuestionPrompt(currentlyTreatedSid,
                "Are you currently being treated for your angiosarcoma?");
        QuestionDef currentlyTreated = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, currentlyTreatedSid, currentlyTreatedPrompt)
                .addOptions(generateYNDKOptions(currentlyTreatedSid))
                .build();

        String currentTherapiesSid = "CURRENT_THERAPIES" + NUANCE;
        Template currentTherapiesPrompt = generateQuestionPrompt(currentTherapiesSid, "Please list the therapies you"
                + " are currently receiving for angiosarcoma (this can include upcoming surgeries, radiation,"
                + " or medications, drugs, or chemotherapies).");
        QuestionBlockDef currentTherapies = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.ESSAY, currentTherapiesSid, currentTherapiesPrompt)
                .build());
        currentTherapies.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, currentlyTreatedSid, "YES"));

        ConditionalBlockDef currentlyTreatedBlock = new ConditionalBlockDef(currentlyTreated);
        currentlyTreatedBlock.addNestedBlock(currentTherapies);
        return currentlyTreatedBlock;
    }

    private ConditionalBlockDef buildOtherCancerBlock() {
        String otherCancerSid = "OTHER_CANCER" + NUANCE;
        Template otherCancerPrompt = generateQuestionPrompt(otherCancerSid, "Were you ever diagnosed with any other kind of cancer(s)?");
        QuestionDef otherCancer = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, otherCancerSid, otherCancerPrompt)
                .addOptions(generateYNDKOptions(otherCancerSid))
                .build();

        Template diseaseNameTmpl = generateTextTemplate("other_cancer_list_name" + NUANCE, "Disease name");
        TextQuestionDef diseaseName = TextQuestionDef.builder(TextInputType.TEXT, "OTHER_CANCER_LIST_NAME" + NUANCE, diseaseNameTmpl)
                .build();

        Template diseaseYearTmpl = generateTextTemplate("other_cancer_list_year" + NUANCE, "Year");
        Template yearRangeHint = generateTextTemplate("other_cancer_list_year_hint" + NUANCE, "Not a valid year (1900 - current year)");
        DateQuestionDef diseaseYear = DateQuestionDef.builder(DateRenderMode.TEXT, "OTHER_CANCER_LIST_YEAR" + NUANCE, diseaseYearTmpl)
                .addFields(DateFieldType.YEAR)
                .addValidation(new DateRangeRuleDef(yearRangeHint, LocalDate.of(1900, 1, 1), null, true))
                .build();

        String otherCancerListSid = "OTHER_CANCER_LIST" + NUANCE;
        Template otherCancerListPrompt = generateQuestionPrompt(otherCancerListSid,
                "Please list which cancer(s) and approximate year(s) of diagnosis.");
        Template addButtonTmpl = generateTextTemplate("other_cancer_list_add_button" + NUANCE, "+ ADD ANOTHER CANCER");
        Template additionalItemTmpl = generateTextTemplate("other_cancer_list_additional_item" + NUANCE, "Other kind of cancer");
        QuestionBlockDef otherCancerList = new QuestionBlockDef(CompositeQuestionDef.builder()
                .setStableId(otherCancerListSid)
                .setPrompt(otherCancerListPrompt)
                .setAllowMultiple(true)
                .setAddButtonTemplate(addButtonTmpl)
                .setAdditionalItemTemplate(additionalItemTmpl)
                .addChildrenQuestions(diseaseName, diseaseYear)
                .build());
        otherCancerList.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, otherCancerSid, "YES"));

        ConditionalBlockDef otherCancerBlock = new ConditionalBlockDef(otherCancer);
        otherCancerBlock.addNestedBlock(otherCancerList);
        return otherCancerBlock;
    }

    private ConditionalBlockDef buildOtherCancerRadiationBlock() {
        String otherCancerRadiationSid = "OTHER_CANCER_RADIATION" + NUANCE;
        Template otherCancerRadiationPrompt = generateQuestionPrompt(otherCancerRadiationSid,
                "Have you had radiation as a treatment for another cancer(s)?");
        QuestionDef otherCancerRadiation = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, otherCancerRadiationSid, otherCancerRadiationPrompt)
                .addOptions(generateYNDKOptions(otherCancerRadiationSid))
                .build();

        String otherCancerRadiationLocSid = "OTHER_CANCER_RADIATION_LOC" + NUANCE;
        Template otherCancerRadiationLocPrompt = generateQuestionPrompt(otherCancerRadiationLocSid,
                "In what part of your body did you receive radiation for your other cancer(s)?");
        QuestionBlockDef otherCancerRadiationLoc = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.ESSAY, otherCancerRadiationLocSid, otherCancerRadiationLocPrompt)
                .build());
        otherCancerRadiationLoc.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, otherCancerRadiationSid, "YES"));

        ConditionalBlockDef otherCancerRadiationBlock = new ConditionalBlockDef(otherCancerRadiation);
        otherCancerRadiationBlock.addNestedBlock(otherCancerRadiationLoc);
        return otherCancerRadiationBlock;
    }

    private QuestionBlockDef buildReferralBlock() {
        String sid = "REFERRAL_SOURCE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "How did you hear about The Angiosarcoma Project?");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt).build());
    }

    private QuestionBlockDef buildExperienceBlock() {
        String sid = "EXPERIENCE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Optional: Tell us anything else you want about yourself"
                + " and your experience with angiosarcoma. We are asking this so you have an opportunity to tell us things"
                + " that you feel are important for our understanding of this disease.");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt).build());
    }

    private QuestionBlockDef buildHispanicBlock() {
        String sid = "HISPANIC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Do you consider yourself Hispanic, Latino/a or Spanish?");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .build());
    }

    private QuestionBlockDef buildRaceBlock() {
        String sid = "RACE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your race (select all that apply)?");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateRaceOptions(sid))
                .build());
    }

    private QuestionBlockDef buildBirthYearBlock() {
        String sid = "BIRTH_YEAR" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "In what year were you born?");
        return new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.PICKLIST, sid, prompt)
                .addFields(DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(null, 0, 119, null, null))
                .build());
    }

    private QuestionBlockDef buildCountryBlock() {
        String sid = "COUNTRY" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What country do you live in?");
        Template picklistLabelTmpl = generateTextTemplate("country_picklist_label" + NUANCE, "Choose country...");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                .setLabel(picklistLabelTmpl)
                .addOptions(generateCountryOptions(sid))
                .build());
    }

    private QuestionBlockDef buildPostalCodeBlock() {
        String sid = "POSTAL_CODE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your ZIP or postal code?");
        Template placeholder = generateTextTemplate(sid + "_placeholder", "Zip Code");
        return new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, sid, prompt)
                .setPlaceholderTemplate(placeholder)
                .build());
    }

    private List<QuestionBlockDef> buildDeprecatedBlocks() {
        return Arrays.asList(
                buildDeprecatedDiagnosisPrimaryLocBlock(),
                buildDeprecatedDiagnosisSpreadBlock(),
                buildDeprecatedDiagnosisSpreadLocBlock(),
                buildDeprecatedPostDiagnosisSpreadBlock(),
                buildDeprecatedPostDiagnosisSpreadLocBlock(),
                buildDeprecatedLocalRecurrenceBlock(),
                buildDeprecatedLocalRecurrenceLocBlock(),
                buildDeprecatedTreatmentPastBlock(),
                buildDeprecatedTreatmentNowBlock(),
                buildDeprecatedDiseaseFreeNowBlock(),
                buildDeprecatedSupportMembershipBlock(),
                buildDeprecatedSupportMembershipTextBlock());
    }

    private QuestionBlockDef buildDeprecatedDiagnosisPrimaryLocBlock() {
        String sid = "DIAGNOSIS_PRIMARY_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "diagnosis primary location");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateDeprecatedBodyLocOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedDiagnosisSpreadBlock() {
        String sid = "DIAGNOSIS_SPREAD" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "diagnosis spread");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedDiagnosisSpreadLocBlock() {
        String sid = "DIAGNOSIS_SPREAD_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "diagnosis spread location");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateDeprecatedBodyLocOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedPostDiagnosisSpreadBlock() {
        String sid = "POST_DIAGNOSIS_SPREAD" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "post diagnosis spread");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedPostDiagnosisSpreadLocBlock() {
        String sid = "POST_DIAGNOSIS_SPREAD_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "post diagnosis spread location");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateDeprecatedBodyLocOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedLocalRecurrenceBlock() {
        String sid = "LOCAL_RECURRENCE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "local recurrence");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedLocalRecurrenceLocBlock() {
        String sid = "LOCAL_RECURRENCE_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "post diagnosis spread location");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateDeprecatedBodyLocOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedTreatmentPastBlock() {
        String sid = "TREATMENT_PAST" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "treatment past");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt)
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedTreatmentNowBlock() {
        String sid = "TREATMENT_NOW" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "treatment now");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt)
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedDiseaseFreeNowBlock() {
        String sid = "DISEASE_FREE_NOW" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "disease free now");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedSupportMembershipBlock() {
        String sid = "SUPPORT_MEMBERSHIP" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "support membership");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .setDeprecated(true)
                .build());
    }

    private QuestionBlockDef buildDeprecatedSupportMembershipTextBlock() {
        String sid = "SUPPORT_MEMBERSHIP_TEXT" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "support membership text");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt)
                .setDeprecated(true)
                .build());
    }

    private List<PicklistOptionDef> generateBodyLocOptions(String prefix) {
        return generateBodyLocationOptions(prefix, false, true);
    }

    private List<PicklistOptionDef> generateBodyLocOptionsWithNoEvidence(String sid) {
        return generateBodyLocationOptions(sid, true, true);
    }

    private List<PicklistOptionDef> generateDeprecatedBodyLocOptions(String sid) {
        return generateBodyLocationOptions(sid, false, false);
    }

    private List<PicklistOptionDef> generateRelativeTimeOptions(String prefix) {
        return generateOptions(prefix, new String[][] {
                {"BEFORE", "Before"},
                {"AFTER", "After"},
                {"BOTH", "Both"},
                {"DK", "I don't know"}});
    }

    private List<PicklistOptionDef> generateRaceOptions(String prefix) {
        return generateOptionsWithDetails(prefix, new String[][] {
                {"AMERICAN_INDIAN", "American Indian or Native American", null},
                {"JAPANESE", "Japanese", null},
                {"CHINESE", "Chinese", null},
                {"OTHER_EAST_ASIAN", "Other East Asian", null},
                {"SOUTH_EAST_ASIAN", "South East Asian or Indian", null},
                {"BLACK", "Black or African American", null},
                {"NATIVE_HAWAIIAN", "Native Hawaiian or other Pacific Islander", null},
                {"WHITE", "White", null},
                {"NO_ANSWER", "I prefer not to answer", null},
                {"OTHER", "Other", "Please provide details"}});
    }

    private List<PicklistOptionDef> generateCountryOptions(String prefix) {
        // Get all official countries sorted by name, excluding US/CA since those should come first.
        List<Locale> locales = Arrays.stream(Locale.getISOCountries())
                .filter(country -> !country.equals("US") && !country.equals("CA"))
                .map(country -> new Locale("", country))
                .sorted(Comparator.comparing(Locale::getDisplayCountry))
                .collect(Collectors.toList());

        String[][] mappings = new String[locales.size() + 2][];
        mappings[0] = new String[] {"US", "United States"};
        mappings[1] = new String[] {"CA", "Canada"};

        for (int i = 0; i < locales.size(); i++) {
            Locale locale = locales.get(i);
            mappings[i + 2] = new String[] {locale.getCountry(), locale.getDisplayCountry()};
        }

        return generateOptions(prefix, mappings);
    }
}

package org.broadinstitute.ddp.script.angio;

import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.READONLY_CONTACT_INFO_HTML;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateBodyLocationOptions;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateHtmlTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateOptionsWithDetails;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateQuestionPrompt;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateTextTemplate;
import static org.broadinstitute.ddp.script.angio.AngioStudyCreationScript.generateYNDKOptions;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.LanguageConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.SummaryTranslation;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.template.TemplateVariable;
import org.broadinstitute.ddp.model.activity.definition.validation.CompleteRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
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
public class AngioLovedOneActivityCreationScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(AngioLovedOneActivityCreationScript.class);

    private static final String USER_GUID = AngioStudyCreationScript.ANGIO_USER_GUID;
    private static final String STUDY_GUID = AngioStudyCreationScript.ANGIO_STUDY_GUID;

    private static final String NUANCE = "";    // Add something here for uniqueness and local testing.
    public static final String ACTIVITY_CODE = "ANGIOLOVEDONE" + NUANCE;

    private static final String PEX_HAS_OPTION_FORMAT = "user.studies[\"" + STUDY_GUID + "\"]"
            + ".forms[\"" + ACTIVITY_CODE + "\"].questions[\"%s\"].answers.hasOption(\"%s\")";

    @Test
    public void insertLovedOneActivity() {
        FormActivityDef lovedOne = FormActivityDef.generalFormBuilder(ACTIVITY_CODE, "v1", STUDY_GUID)
                .setListStyleHint(ListStyleHint.NUMBER)
                .setMaxInstancesPerUser(1)
                .setWriteOnce(false)
                .setDisplayOrder(4)
                .addName(new Translation("en", "Join the movement: tell us about your loved one"))
                .addDashboardName(new Translation("en", "Loved One Survey"))
                .setIntroduction(buildIntroSection())
                .addSections(buildBodySections())
                .setReadonlyHintTemplate(buildReadonlyHintTemplate())
                .addSummaries(buildDashboardSummaries())
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            long startMillis = AngioStudyCreationScript.ACTIVITY_TIMESTAMP_ANCHOR;
            RevisionMetadata meta = new RevisionMetadata(startMillis, userId, "Creating angio loved-one activity");
            handle.attach(ActivityDao.class).insertActivity(lovedOne, meta);
            assertNotNull(lovedOne.getActivityId());

            Gson gson = GsonUtil.standardBuilder().setPrettyPrinting().create();
            LOG.info("Created angio loved-one activity code={} id={} json=\n{}",
                    lovedOne.getActivityCode(), lovedOne.getActivityId(), gson.toJson(lovedOne));
        });
    }

    private Template buildReadonlyHintTemplate() {
        return generateHtmlTemplate("angio_loved_one_readonly_hint", "<span class=\"ddp-block-title-bold\">"
                + "Thank you for submitting your survey. " + READONLY_CONTACT_INFO_HTML + "</span>");
    }

    private Collection<SummaryTranslation> buildDashboardSummaries() {
        Collection<SummaryTranslation> dashboardSummaries = new ArrayList<>();
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "Completing the Loved One Survey will tell us about your loved one's experiences with angiosarcoma",
                InstanceStatusType.CREATED));
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "Submitting the Loved One Survey will tell us about your loved one's experiences with angiosarcoma",
                InstanceStatusType.IN_PROGRESS));
        dashboardSummaries.add(new SummaryTranslation(LanguageConstants.EN_LANGUAGE_CODE,
                "All set - thank you for sharing loved one's experiences with angiosarcoma",
                InstanceStatusType.COMPLETE));
        return dashboardSummaries;
    }

    private FormSectionDef buildIntroSection() {
        String thankYouText = "Thank you for providing your contact information. Please help us understand more"
                + " about your loved one's angiosarcoma by answering the questions below.";
        String autoSaveText = "As you fill out the questions below, your answers will be automatically saved."
                + " If you’ve previously entered information here and want to pick up where you left off,"
                + " please use the link we sent you via email to return to this page.";
        String emailDeleteText = "If you would like your information deleted from our database, please let us know"
                + " by emailing <a href=\"mailto:info@ascproject.org\" class=\"Link\">info@ascproject.org</a> and we"
                + " will remove your name and email address and the answers to any questions you may have answered.";

        Template bodyTmpl = Template.html("<div class=\"PageContent-box\">"
                + "<p class=\"PageContent-text\">$intro_thank_you</p>"
                + "<p class=\"PageContent-text\">$intro_auto_save</p>"
                + "<p class=\"PageContent-text\">$intro_email_for_delete</p>"
                + "</div>");
        bodyTmpl.addVariable(TemplateVariable.single("intro_thank_you", "en", thankYouText));
        bodyTmpl.addVariable(TemplateVariable.single("intro_auto_save", "en", autoSaveText));
        bodyTmpl.addVariable(TemplateVariable.single("intro_email_for_delete", "en", emailDeleteText));

        ContentBlockDef intro = new ContentBlockDef(null, bodyTmpl);
        return new FormSectionDef("angio_loved_one_intro" + NUANCE, Collections.singletonList(intro));
    }

    private List<FormSectionDef> buildBodySections() {
        FormSectionDef body = new FormSectionDef("angio_loved_one_body" + NUANCE, Arrays.asList(
                buildInstructionBlock(),

                buildRelationToBlock(),
                buildFirstNameBlock(),
                buildLastNameBlock(),
                buildDateOfBirthBlock(),

                buildDiagnosisPostalCodeBlock(),
                buildPassedPostalCodeBlock(),
                buildDiagnosisDateBlock(),
                buildPassingDateBlock(),

                buildDiagnosisPrimaryLocBlock(),
                buildDiagnosisSpreadLocBlock(),
                buildOtherCancerBlock(),
                buildExperienceBlock(),
                buildFutureContactBlock()
        ));
        return Collections.singletonList(body);
    }

    private ContentBlockDef buildInstructionBlock() {
        String titleText = "About your loved one";
        String bodyText = "Please fill out as much as you can. All questions are optional."
                + " You can return at any time with the link sent to you by email.";

        Template titleTmpl = Template.html("<h1 class=\"PageContent-title\">$instruction_title</h1>");
        titleTmpl.addVariable(TemplateVariable.single("instruction_title", "en", titleText));

        Template bodyTmpl = Template.html("<p class=\"PageContent-text\">$instruction_body</p>");
        bodyTmpl.addVariable(TemplateVariable.single("instruction_body", "en", bodyText));

        return new ContentBlockDef(titleTmpl, bodyTmpl);
    }

    private QuestionBlockDef buildRelationToBlock() {
        String sid = "LOVEDONE_RELATION_TO" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your relation to your loved one?");
        Template label = generateTextTemplate("lovedone_relation_to_select_label", "Select...");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                .setLabel(label)
                .addOptions(generateRelationOptions(sid))
                .build());
    }

    private QuestionBlockDef buildFirstNameBlock() {
        String sid = "LOVEDONE_FIRST_NAME" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your loved one's first name?");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build());
    }

    private QuestionBlockDef buildLastNameBlock() {
        String sid = "LOVEDONE_LAST_NAME" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your loved one's last name?");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build());
    }

    private QuestionBlockDef buildDateOfBirthBlock() {
        String sid = "LOVEDONE_DOB" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What is your loved one's date of birth? (MM-DD-YYYY)");
        Template dobCompleteHint = generateTextTemplate(sid + "_complete_hint" + NUANCE,
                "Please enter your loved one's date of birth in MM DD YYYY format.");
        Template dobRangeHint = generateTextTemplate(sid + "_range_hint" + NUANCE,
                "Please enter your loved one's date of birth in MM DD YYYY format.");
        return new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.TEXT, sid, prompt)
                .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                .addValidation(new CompleteRuleDef(dobCompleteHint))
                .addValidation(new DateRangeRuleDef(dobRangeHint, LocalDate.of(1898, 1, 1), null, true))
                .build());
    }

    private QuestionBlockDef buildDiagnosisPostalCodeBlock() {
        String sid = "LOVEDONE_DIAGNOSIS_POSTAL_CODE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What zip code did your loved one live in when diagnosed with angiosarcoma?");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build());
    }

    private QuestionBlockDef buildPassedPostalCodeBlock() {
        String sid = "LOVEDONE_PASSED_POSTAL_CODE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "What zip code did your loved one live in when they passed?");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.TEXT, sid, prompt).build());
    }

    private QuestionBlockDef buildDiagnosisDateBlock() {
        String sid = "LOVEDONE_DIAGNOSIS_DATE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "When was your loved one diagnosed with angiosarcoma?");
        return new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.PICKLIST, sid, prompt)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(true, 0, 119, null, null))
                .build());
    }

    private QuestionBlockDef buildPassingDateBlock() {
        String sid = "LOVEDONE_PASSING_DATE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "When did your loved one pass away?");
        return new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.PICKLIST, sid, prompt)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(true, 0, 119, null, null))
                .build());
    }

    private QuestionBlockDef buildDiagnosisPrimaryLocBlock() {
        String sid = "LOVEDONE_DIAGNOSIS_PRIMARY_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "When your loved one was first diagnosed with angiosarcoma,"
                + " where in their body was it found?");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateBodyLocOptions(sid))
                .build());
    }

    private QuestionBlockDef buildDiagnosisSpreadLocBlock() {
        String sid = "LOVEDONE_DIAGNOSIS_SPREAD_LOC" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Please list where else in their body your loved one's angiosarcoma spread.");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildMultiSelect(PicklistRenderMode.LIST, sid, prompt)
                .addOptions(generateBodyLocOptions(sid))
                .build());
    }

    private ConditionalBlockDef buildOtherCancerBlock() {
        String otherCancerSid = "LOVEDONE_OTHER_CANCER" + NUANCE;
        Template otherCancerPrompt = generateQuestionPrompt(otherCancerSid,
                "Was your loved one ever diagnosed with another kind of cancer(s)?");
        QuestionDef otherCancer = PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, otherCancerSid, otherCancerPrompt)
                .addOptions(generateYNDKOptions(otherCancerSid))
                .build();

        String radiationSid = "LOVEDONE_OTHER_CANCER_RADIATION" + NUANCE;
        Template radiationPrompt = generateQuestionPrompt(radiationSid,
                "Did your loved one ever have radiation as a treatment for their other cancer(s)?");
        List<PicklistOptionDef> radiationOptions = generateOptionsWithDetails(radiationSid, new String[][] {
                {"YES", "Yes", "Please describe in which part(s) of their body"},
                {"NO", "No", null},
                {"DK", "I don't know", null}
        });
        QuestionBlockDef radiation = new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, radiationSid, radiationPrompt)
                .addOptions(radiationOptions)
                .build());
        radiation.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, otherCancerSid, "YES"));

        String moreTextSid = "LOVEDONE_OTHER_CANCER_TEXT" + NUANCE;
        Template moreTextPrompt = generateQuestionPrompt(moreTextSid, "Please tell us more about your loved one's other cancer(s).");
        QuestionBlockDef moreText = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.ESSAY, moreTextSid, moreTextPrompt)
                .build());
        moreText.setShownExpr(String.format(PEX_HAS_OPTION_FORMAT, otherCancerSid, "YES"));

        ConditionalBlockDef otherCancerBlock = new ConditionalBlockDef(otherCancer);
        otherCancerBlock.addNestedBlock(radiation);
        otherCancerBlock.addNestedBlock(moreText);

        return otherCancerBlock;
    }

    private QuestionBlockDef buildExperienceBlock() {
        String sid = "LOVEDONE_EXPERIENCE" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "Please tell us anything you think is important for us to know"
                + " about your loved one's experience with angiosarcoma.");
        return new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, sid, prompt).build());
    }

    private QuestionBlockDef buildFutureContactBlock() {
        String sid = "LOVEDONE_FUTURE_CONTACT" + NUANCE;
        Template prompt = generateQuestionPrompt(sid, "We are currently exploring ways to include medical records"
                + " and tissue from loved ones in our studies, may we contact you in the future regarding this?");
        return new QuestionBlockDef(PicklistQuestionDef
                .buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOptions(generateYNDKOptions(sid))
                .build());
    }

    private List<PicklistOptionDef> generateRelationOptions(String prefix) {
        return generateOptionsWithDetails(prefix, new String[][] {
                {"PARENT", "Parent", null},
                {"SIBLING", "Sibling", null},
                {"CHILD", "Child", null},
                {"AUNT_UNCLE", "Aunt/Uncle", null},
                {"SPOUSE", "Spouse", null},
                {"FRIEND", "Friend", null},
                {"OTHER", "Other", "Please specify"}});
    }

    private List<PicklistOptionDef> generateBodyLocOptions(String prefix) {
        return generateBodyLocationOptions(prefix, false, true);
    }
}

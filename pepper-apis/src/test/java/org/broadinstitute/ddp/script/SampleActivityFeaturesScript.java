package org.broadinstitute.ddp.script;

import static org.broadinstitute.ddp.model.activity.types.InstanceStatusType.CREATED;
import static org.junit.Assert.assertNotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.ConditionalBlockDef;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.GroupBlockDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.CompositeQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistSelectMode;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.GsonUtil;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class SampleActivityFeaturesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(SampleActivityFeaturesScript.class);

    private static final String USER_GUID = TestConstants.TEST_USER_GUID;
    private static final String STUDY_GUID = TestConstants.TEST_STUDY_GUID;
    private static final String ACT_CODE_SAMPLE_SURVEY = "ACT_SAMPLE_SURVEY";
    private static String STABLE_ID_SUFFIX = "_5";

    @Test
    @Ignore
    public void createSampleSurvey() {
        String introText = "<div class=\"PageContent-box\">"
                + "<p>Please help us understand more about your disease by answering the questions below.</p>"
                + "<p>As you fill out the questions below, your answers will be automatically saved.</p>"
                + "</div>";
        ContentBlockDef introContent = new ContentBlockDef(Template.html(introText));
        FormSectionDef intro = new FormSectionDef(null, Collections.singletonList(introContent));

        GroupBlockDef groupNumbered = new GroupBlockDef(ListStyleHint.NUMBER, null);
        groupNumbered.getNested().add(new ContentBlockDef(
                Template.text("What is the purpose of this study?"),
                Template.text("We want to understand your disease better so that we can develop more effective therapies.")));
        groupNumbered.getNested().add(new ContentBlockDef(
                Template.text("What if I have questions?"),
                Template.html("<p>Please call and speak with us at <a href=\"tel:123-456-7890\">123-456-7890</a>.</p>")));

        GroupBlockDef groupAlpha = new GroupBlockDef(ListStyleHint.UPPER_ALPHA, Template.text("Below contains more info:"));
        groupAlpha.getNested().add(new ContentBlockDef(
                Template.text("What is involved in the research study?"),
                Template.html("<p>We will first ask you some question.</p><p>Then we will collect and analysis your data.</p>")));
        groupAlpha.getNested().add(new ContentBlockDef(
                Template.text("Authorization to use your health information for research purposes"),
                Template.html(buildAuthorizationContentText())));

        GroupBlockDef groupBullet = new GroupBlockDef(ListStyleHint.BULLET, Template.text("This is what I agree to:"));
        groupBullet.getNested().add(new QuestionBlockDef(BoolQuestionDef.builder("SURVEY_Q_BLOOD_DRAW" + STABLE_ID_SUFFIX,
                Template.text("You can arrange a blood draw."), Template.text("Yes"), Template.text("No")).build()));
        groupBullet.getNested().add(new QuestionBlockDef(BoolQuestionDef.builder("SURVEY_Q_TISSUE_SAMPLES" + STABLE_ID_SUFFIX,
                Template.text("You can request my tissue samples."), Template.text("Yes"), Template.text("No")).build()));

        QuestionBlockDef q1 = new QuestionBlockDef(DateQuestionDef.builder()
                .setStableId("SURVEY_Q_FIRST_DIAGNOSED" + STABLE_ID_SUFFIX)
                .setPrompt(Template.html("When were you <em>first diagnosed</em> with disease?"))
                .setRenderMode(DateRenderMode.PICKLIST)
                .setDisplayCalendar(false)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .setPicklistDef(new DatePicklistDef(true, 0, 100, null, null))
                .build());

        String interludeText = "<div class=\"PageContent-box\"><p>"
                + "To help us understand the full scope of how your disease was treated, the following questions will ask you separately"
                + " about surgery, radiation, and any medications, drugs, or chemotherapies you may have received for disease."
                + "</p></div>";
        ContentBlockDef interlude = new ContentBlockDef(Template.text(interludeText));

        QuestionDef q2Control = PicklistQuestionDef.builder()
                .setStableId("SURVEY_Q_RADIATION" + STABLE_ID_SUFFIX)
                .setPrompt(Template.html("Have you had <strong>radiation as a treatment</strong> for the disease?"))
                .setSelectMode(PicklistSelectMode.SINGLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .addOption(new PicklistOptionDef("RADIATION_YES" + STABLE_ID_SUFFIX, Template.text("Yes")))
                .addOption(new PicklistOptionDef("RADIATION_NO" + STABLE_ID_SUFFIX, Template.text("No")))
                .addOption(new PicklistOptionDef("RADIATION_DK" + STABLE_ID_SUFFIX, Template.text("I don't know")))
                .build();
        QuestionBlockDef q2Nested = new QuestionBlockDef(PicklistQuestionDef.builder()
                .setStableId("SURVEY_Q_RADIATION_TIME" + STABLE_ID_SUFFIX)
                .setPrompt(Template.html("If so, was your radiation before or after surgery?"))
                .setSelectMode(PicklistSelectMode.SINGLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .addOption(new PicklistOptionDef("RADIATION_BEFORE" + STABLE_ID_SUFFIX, Template.text("Before")))
                .addOption(new PicklistOptionDef("RADIATION_AFTER" + STABLE_ID_SUFFIX, Template.text("After")))
                .addOption(new PicklistOptionDef("RADIATION_BOTH" + STABLE_ID_SUFFIX, Template.text("Both")))
                .addOption(new PicklistOptionDef("RADIATION_DK" + STABLE_ID_SUFFIX, Template.text("I don't know")))
                .build());
        String radiationExpr = String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"%s\")",
                STUDY_GUID, ACT_CODE_SAMPLE_SURVEY + STABLE_ID_SUFFIX, "SURVEY_Q_RADIATION" + STABLE_ID_SUFFIX,
                "RADIATION_YES" + STABLE_ID_SUFFIX);
        q2Nested.setShownExpr(radiationExpr);
        ConditionalBlockDef q2 = new ConditionalBlockDef(q2Control);
        q2.getNested().add(q2Nested);

        QuestionDef q3Control = PicklistQuestionDef.builder()
                .setStableId("SURVEY_Q_TREATED" + STABLE_ID_SUFFIX)
                .setPrompt(Template.html("Are you <ins>currently being treated</ins> for your disease?"))
                .setSelectMode(PicklistSelectMode.SINGLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .addOption(new PicklistOptionDef("TREATED_YES" + STABLE_ID_SUFFIX, Template.text("Yes")))
                .addOption(new PicklistOptionDef("TREATED_NO" + STABLE_ID_SUFFIX, Template.text("No")))
                .addOption(new PicklistOptionDef("TREATED_DK" + STABLE_ID_SUFFIX, Template.text("I don't know")))
                .build();
        String nestedText = "<p>Treatment therapies can include upcoming surgeries, radiation, medications, drugs, or chemotherapies.</p>";
        ContentBlockDef q3NestedContent = new ContentBlockDef(Template.html(nestedText));
        QuestionBlockDef q3NestedQuestion = new QuestionBlockDef(TextQuestionDef.builder()
                .setStableId("SURVEY_Q_TREATED_LIST" + STABLE_ID_SUFFIX)
                .setPrompt(Template.html("Please list the therapies you are currently receiving for your disease."))
                .setInputType(TextInputType.ESSAY)
                .build());
        String treatedExpr = String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"%s\")",
                STUDY_GUID, ACT_CODE_SAMPLE_SURVEY + STABLE_ID_SUFFIX, "SURVEY_Q_TREATED" + STABLE_ID_SUFFIX,
                "TREATED_YES" + STABLE_ID_SUFFIX);
        q3NestedContent.setShownExpr(treatedExpr);
        q3NestedQuestion.setShownExpr(treatedExpr);
        ConditionalBlockDef q3 = new ConditionalBlockDef(q3Control);
        q3.getNested().add(q3NestedContent);
        q3.getNested().add(q3NestedQuestion);

        QuestionDef q4Control = PicklistQuestionDef.builder()
                .setStableId("SURVEY_Q_OTHER_DISEASES" + STABLE_ID_SUFFIX)
                .setPrompt(Template.text("Were you ever diagnosed with any other kind of cancer(s)?"))
                .setSelectMode(PicklistSelectMode.SINGLE)
                .setRenderMode(PicklistRenderMode.LIST)
                .addOption(new PicklistOptionDef("OTHER_DISEASES_YES" + STABLE_ID_SUFFIX, Template.text("Yes")))
                .addOption(new PicklistOptionDef("OTHER_DISEASES_NO" + STABLE_ID_SUFFIX, Template.text("No")))
                .addOption(new PicklistOptionDef("OTHER_DISEASES_DK" + STABLE_ID_SUFFIX, Template.text("I don't know")))
                .build();
        QuestionBlockDef q4Nested = new QuestionBlockDef(CompositeQuestionDef.builder()
                .setStableId("SURVEY_Q_OTHER_DISEASES_LIST" + STABLE_ID_SUFFIX)
                .setPrompt(Template.text("Please list which cancer(s) and approximate year(s) of diagnosis."))
                .setAllowMultiple(true)
                .setAddButtonTemplate(Template.text("+ ADD ANOTHER CANCER"))
                .setAdditionalItemTemplate(Template.text("Other kind of cancer"))
                .addChildrenQuestions(
                        TextQuestionDef.builder()
                                .setStableId("SURVEY_Q_OTHER_DISEASES_LIST_NAME" + STABLE_ID_SUFFIX)
                                .setPrompt(Template.text("Disease name"))
                                .setInputType(TextInputType.TEXT)
                                .build(),
                        DateQuestionDef.builder()
                                .setStableId("SURVEY_Q_OTHER_DISEASES_LIST_YEAR" + STABLE_ID_SUFFIX)
                                .setPrompt(Template.text("Year"))
                                .setRenderMode(DateRenderMode.TEXT)
                                .setDisplayCalendar(false)
                                .addFields(DateFieldType.YEAR)
                                .build())
                .build());
        String otherDiseasesExpr = String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasOption(\"%s\")",
                STUDY_GUID, ACT_CODE_SAMPLE_SURVEY + STABLE_ID_SUFFIX, "SURVEY_Q_OTHER_DISEASES" + STABLE_ID_SUFFIX,
                "OTHER_DISEASES_YES" + STABLE_ID_SUFFIX);
        q4Nested.setShownExpr(otherDiseasesExpr);
        ConditionalBlockDef q4 = new ConditionalBlockDef(q4Control);
        q4.getNested().add(q4Nested);

        FormSectionDef body = new FormSectionDef(null, Arrays.asList(groupNumbered, groupAlpha, groupBullet, q1, interlude, q2, q3, q4));

        String closingText = "<p>I understand that the information I entered here will be stored in a secure database and may be used to"
                + " match me to one or more research studies conducted by the research project. If the information that I entered matches"
                + " a study being conducted, either now or in the future, I agree to be contacted about possibly participating. I"
                + " understand that if I would like my information deleted from the database, now or in the future, I can email"
                + " <a href=\"mailto:fake-no-reply@datadonationplatform.org\">fake-no-reply@datadonationplatform.org</a> and my"
                + " information will be removed from the database.</p>";
        ContentBlockDef closingContent = new ContentBlockDef(Template.html(closingText));

        List<RuleDef> rules = Collections.singletonList(new RequiredRuleDef(Template.text("Please give your agreement.")));
        AgreementQuestionDef agreement = new AgreementQuestionDef("SURVEY_Q_AGREE" + STABLE_ID_SUFFIX,
                                                                    false,
                                                                    Template.html("I agree to the above."),
                                                                    null,
                                                                    null,
                                                                    rules,
                                                                    true);
        QuestionBlockDef closingAgreement = new QuestionBlockDef(agreement);
        FormSectionDef closing = new FormSectionDef(null, Arrays.asList(closingContent, closingAgreement));

        FormActivityDef survey = FormActivityDef.generalFormBuilder(ACT_CODE_SAMPLE_SURVEY + STABLE_ID_SUFFIX, "v1", STUDY_GUID)
                .addName(new Translation("en", "Sample Survey Activity"))
                .setDisplayOrder(10)
                .setListStyleHint(ListStyleHint.NUMBER)
                .setIntroduction(intro)
                .addSection(body)
                .setClosing(closing)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample survey activity");
            handle.attach(ActivityDao.class).insertActivity(survey, meta);
            assertNotNull(survey.getActivityId());

            ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
            ActivityInstanceDto createdActivityInstance = activityInstanceDao.insertInstance(
                    survey.getActivityId(), USER_GUID, USER_GUID, CREATED, false);

            assertNotNull(createdActivityInstance.getGuid());
            LOG.info("Created activity code={} id={} json=\n{}", ACT_CODE_SAMPLE_SURVEY, survey.getActivityId(),
                    GsonUtil.standardGson().toJson(survey));

            LOG.info("Created activity instance with GUID={} for User GUID={}" + createdActivityInstance.getGuid(),
                    USER_GUID);
        });
    }

    private String buildAuthorizationContentText() {
        return "<p class=\"PageContent-text\">Because information about you and your health is personal and private, it generally cannot"
                + " be used in this research study without your written authorization. Federal law requires that your health care providers"
                + " and healthcare institutions (hospitals, clinics, doctor's offices) protect the privacy of information that identifies"
                + " you and relates to your past, present, and future physical and mental health conditions.</p>"
                + "<p class=\"PageContent-text\">If you complete this form, it will provide your health care providers and healthcare"
                + " institutions the authorization to disclose your protected health information to the researchers for use in this"
                + " research study. The form is intended to inform you about how your health information will be used or disclosed in the"
                + " study. Your information will only be used in accordance with this authorization form and the informed consent form and"
                + " as required or allowed by law. Please read it carefully before signing it.</p>"
                + "<ol class=\"PageContent-ol\">"
                + "  <li>"
                + "    <p class=\"PageContent-text\">What personal information about me will be used or shared with others?</p>"
                + "    <ul>"
                + "      <li class=\"PageContent-text\">Health information created from study-related tests and/or questionnaires</li>"
                + "      <li class=\"PageContent-text\">Your medical records</li>"
                + "      <li class=\"PageContent-text\">Your saliva sample</li>"
                + "    </ul>"
                + "    <p class=\"PageContent-text\"><b>If elected (later in this form):</b></p>"
                + "    <ul>"
                + "      <li class=\"PageContent-text\">Your blood sample</li>"
                + "      <li class=\"PageContent-text\">Your tissue samples relevant to this research study and related records</li>"
                + "    </ul>"
                + "  </li>"
                + "  <li>"
                + "    <p class=\"PageContent-text\">Why will protected information about me be used or shared with others?</p>"
                + "    <p class=\"PageContent-text\">The main reasons include the following:</p>"
                + "    <ul>"
                + "      <li class=\"PageContent-text\">To conduct and oversee the research described earlier in this form;</li>"
                + "      <li class=\"PageContent-text\">To ensure the research meets legal, institutional, and other requirements;</li>"
                + "      <li class=\"PageContent-text\">To conduct public health activities (including reporting of adverse events or"
                + " situations where you or others may be at risk of harm)</li>"
                + "    </ul>"
                + "  </li>"
                + "  <li>"
                + "    <p class=\"PageContent-text\">Who will use or share protected health information about me?</p>"
                + "    <p class=\"PageContent-text\">The researchers and affiliated research staff will use and/or share your personal"
                + " health information in connection with this research study.</p>"
                + "  </li>"
                + "  <li>"
                + "    <p class=\"PageContent-text\">With whom outside of the research may my personal health information be shared?</p>"
                + "    <p class=\"PageContent-text\">While all reasonable efforts will be made to protect the confidentiality of your"
                + " protected health information, it may also be shared with the following entities:</p>"
                + "    <ul>"
                + "      <li class=\"PageContent-text\">Federal and state agencies (for example, the Department of Health and Human"
                + " Services, the Food and Drug Administration, the National Institutes of Health, and/or the Office for Human Research"
                + " Protections), or other domestic or foreign government bodies if required by law and/or necessary for oversight"
                + " purposes. A qualified representative of the FDA and the National Cancer Institute may review your medical records.</li>"
                + "      <li class=\"PageContent-text\">Outside individuals or entities that have a need to access this information to"
                + " perform functions relating to the conduct of this research such as data storage companies.</li>"
                + "    </ul>"
                + "    <p class=\"PageContent-text\">Some who may receive your personal health information may not have to satisfy the"
                + " privacy rules and requirements. They, in fact, may share your information with others without your permission.</p>"
                + "  </li>"
                + "  <li>"
                + "    <p class=\"PageContent-text\">How long will protected health information about me be used or shared with others?</p>"
                + "    <p class=\"PageContent-text\">There is no scheduled date at which your protected health information that is being"
                + " used or shared for this research will be destroyed, because research is an ongoing process.</p>"
                + "  </li>"
                + "  <li>"
                + "    <p class=\"PageContent-text\">Statement of privacy rights:</p>"
                + "    <ul>"
                + "      <li class=\"PageContent-text\">You have the right to withdraw your permission for the doctors and researchers to"
                + " use or share your protected health information. We will not be able to withdraw all the information that already has"
                + " been used or shared with others to carry out related activities such as oversight, or that is needed to ensure quality"
                + " of the study. To withdraw your permission, you must do so in writing by contacting the researcher</li>"
                + "      <li class=\"PageContent-text\">You have the right to request access to your personal health information that is"
                + " used or shared during this research and that is related to your treatment or payment for your treatment. To request"
                + " this information, please contact your doctor who will request this information from the study directors.</li>"
                + "    </ul>"
                + "  </li>"
                + "</ol>";
    }
}

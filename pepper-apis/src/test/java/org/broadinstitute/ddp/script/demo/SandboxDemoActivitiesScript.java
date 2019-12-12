package org.broadinstitute.ddp.script.demo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.ContentBlockDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
import org.broadinstitute.ddp.model.activity.definition.QuestionBlockDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.definition.question.AgreementQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.BoolQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.DatePicklistDef;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.definition.validation.DateFieldRequiredRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.ListStyleHint;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestUtil;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Ignore
public class SandboxDemoActivitiesScript extends TxnAwareBaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(SandboxDemoActivitiesScript.class);

    private static final String USER_GUID = TestConstants.TEST_USER_GUID;
    private static final String STUDY_GUID = TestConstants.TEST_STUDY_GUID;

    private static Gson gson;

    @BeforeClass
    public static void setup() {
        gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    }

    @Test
    @Ignore
    public void insertActivity_picklistRadioCheckboxMode() {
        String sid = "PL_RADIO_CHECKBOX";
        Template prompt = new Template(TemplateType.TEXT, null, "This question demonstrates radio checkbox, not required.");
        PicklistQuestionDef picklist = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sid, prompt)
                .addOption(new PicklistOptionDef("OPT_YES", new Template(TemplateType.TEXT, null, "Yes")))
                .addOption(new PicklistOptionDef("OPT_NO", new Template(TemplateType.TEXT, null, "No")))
                .addOption(new PicklistOptionDef("OPT_DONT_KNOW", new Template(TemplateType.TEXT, null, "I don't know")))
                .build();

        String sidReq = "PL_RADIO_CHECKBOX_REQ";
        Template promptReq = new Template(TemplateType.TEXT, null, "This picklist question is required. *");
        PicklistQuestionDef picklistReq = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.CHECKBOX_LIST, sidReq, promptReq)
                .addOption(new PicklistOptionDef("OPT_BEFORE", new Template(TemplateType.TEXT, null, "Before")))
                .addOption(new PicklistOptionDef("OPT_AFTER", new Template(TemplateType.TEXT, null, "After")))
                .addOption(new PicklistOptionDef("OPT_BOTH", new Template(TemplateType.TEXT, null, "Both")))
                .addValidation(new RequiredRuleDef(new Template(TemplateType.TEXT, null, "An option needs to be selected")))
                .build();

        String actCode = "ACT_SAMPLE_PL_RADIO_CHECKBOX";
        FormActivityDef form = FormActivityDef.generalFormBuilder(actCode, "v1", STUDY_GUID)
                .addName(new Translation("en", "Sample Activity for Picklist Radio Checkbox Mode"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(picklist, picklistReq)))
                .setDisplayOrder(30)
                .build();

        TransactionWrapper.useTxn(handle -> {
            ActivityDao actDao = handle.attach(ActivityDao.class);
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);

            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            String reason = "add sample activity for picklist radio checkbox mode to test study";
            actDao.insertActivity(form, RevisionMetadata.now(userId, reason));
            assertNotNull(form.getActivityId());
            LOG.info("Created activity {} with id={} json=\n{}", form.getActivityCode(), form.getActivityId(), gson.toJson(form));

            int numUpdated = handle.attach(JdbiActivity.class).updateAutoInstantiateById(form.getActivityId(), true);
            assertEquals("failed to make activity auto-instantiated", 1, numUpdated);

            ActivityInstanceDto instanceDto = instanceDao.insertInstance(form.getActivityId(), USER_GUID);
            LOG.info("Created activity instance {} for user {}", instanceDto.getGuid(), USER_GUID);

            handle.rollback();      // comment this to persist
            LOG.info("Rolled back insertions");
        });
    }

    @Test
    @Ignore
    public void insertActivity_conditionalFormContentToggle() {
        QuestionBlockDef controller = new QuestionBlockDef(BoolQuestionDef.builder("195FC87948_CONTROL_Q",
                Template.text("This question controls visibility of the content block below."),
                Template.text("true / yes / show"), Template.text("false / no / hide")).build());

        ContentBlockDef toggled = new ContentBlockDef(Template.text("This content is toggled by above question."));
        toggled.setShownExpr(String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                STUDY_GUID, "CA486F22EA", "195FC87948_CONTROL_Q"));

        FormSectionDef body = new FormSectionDef(null, Arrays.asList(controller, toggled));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("CA486F22EA", "v1", STUDY_GUID)
                .addName(new Translation("en", "Conditional Form for Testing Content Toggle"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(40)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert conditional form for toggling content");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }

    @Test
    @Ignore
    public void insertActivity_conditionalFormQuestionToggle() {
        QuestionBlockDef controller = new QuestionBlockDef(BoolQuestionDef.builder("9028507D7_CONTROL_Q",
                Template.text("This question controls visibility of the question block below."),
                Template.text("true / yes / show"), Template.text("false / no / hide")).build());

        QuestionBlockDef toggled = new QuestionBlockDef(TextQuestionDef
                .builder(TextInputType.TEXT, "D813B49E6B_TOGGLED_Q", Template.text("This text question is toggled by above question."))
                .addValidation(new RequiredRuleDef(Template.text("Please correct your answer")))
                .addValidation(new LengthRuleDef(Template.text("Please correct your answer"), 10, 20))
                .build());
        toggled.setShownExpr(String.format("user.studies[\"%s\"].forms[\"%s\"].questions[\"%s\"].answers.hasTrue()",
                STUDY_GUID, "2E3A3285C7", "9028507D7_CONTROL_Q"));

        FormSectionDef body = new FormSectionDef(null, Arrays.asList(controller, toggled));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("2E3A3285C7", "v1", STUDY_GUID)
                .addName(new Translation("en", "Conditional Form for Testing Question Toggle"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(50)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert conditional form for toggling question");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }

    @Test
    @Ignore
    public void insertActivity_dateQuestion() {
        QuestionBlockDef dateTextQ = new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.TEXT, "524C2B7FDD_DATE_TEXT_Q", Template.text("Please enter month/year"))
                .setDisplayCalendar(false)
                .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                .addValidation(new RequiredRuleDef(null))
                .build());

        QuestionBlockDef dateSingleTextQ = new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.SINGLE_TEXT, "C8D1F9BB24_DATE_SINGLE_TEXT_Q", Template.text("Please enter year/month/day"))
                .setDisplayCalendar(true)
                .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                .addValidation(new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, null))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, null))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, null))
                .addValidation(new DateRangeRuleDef(null, LocalDate.now(), null, false))
                .build());

        QuestionBlockDef datePicklistQ = new QuestionBlockDef(DateQuestionDef
                .builder(DateRenderMode.PICKLIST, "1413E81EEC_DATE_PICKLIST_Q", Template.text("Please select month/day/year"))
                .setDisplayCalendar(true)
                .setPicklistDef(new DatePicklistDef(true, 3, 80, null, 1988))
                .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR)
                .addValidation(new RequiredRuleDef(Template.text("Please select a date.")))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.YEAR_REQUIRED, null))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.MONTH_REQUIRED, null))
                .addValidation(new DateFieldRequiredRuleDef(RuleType.DAY_REQUIRED, null))
                .addValidation(new DateRangeRuleDef(Template.text("Please select a date on or before Pi Day March 14, 2018."),
                        null, LocalDate.of(2018, 3, 14), false))
                .build());

        FormSectionDef body = new FormSectionDef(null, Arrays.asList(dateTextQ, dateSingleTextQ, datePicklistQ));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("A3DCBECC19", "v1", STUDY_GUID)
                .addName(new Translation("en", "Sample Form for Testing Date Question Type"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(60)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample date question activity");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }

    @Test
    @Ignore
    public void insertActivity_agreementQuestion() {
        AgreementQuestionDef agreementDef = new AgreementQuestionDef("AGREEMENT_Q",
                                                        false,
                                                        Template.text("I agree with the preceding text"),
                                                        null,
                                                        null,
                                                        Collections.singletonList(new RequiredRuleDef(Template.text("This is required"))),
                                                        true);
        QuestionBlockDef question = new QuestionBlockDef(agreementDef);

        FormSectionDef body = new FormSectionDef(null, Collections.singletonList(question));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("ACTIVITY_FOR_TESTING_AGREEMENT_QUESTION", "v1", STUDY_GUID)
                .addName(new Translation("en", "Activity for testing the agreement question type"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(70)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample agreement question activity");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }

    @Test
    @Ignore
    public void insertActivity_textEssayQuestion() {
        QuestionBlockDef question = new QuestionBlockDef(TextQuestionDef.builder(TextInputType.ESSAY, "ESSAY_TEXT_Q",
                Template.text("Your dream destination")).build());

        FormSectionDef body = new FormSectionDef(null, Collections.singletonList(question));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("ACTIVITY_FOR_TESTING_ESSAY_TEXT_QUESTION", "v1", STUDY_GUID)
                .addName(new Translation("en", "Activity for testing the essay text question type"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(80)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample text essay activity");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }

    @Test
    @Ignore
    public void insertActivity_readonlyActivity() {
        QuestionBlockDef question = new QuestionBlockDef(BoolQuestionDef.builder("ROQUESTION",
                Template.text("Do you like ice cream?"), Template.text("Yes, of course!"), Template.text("No!")).build());

        FormSectionDef body = new FormSectionDef(null, Collections.singletonList(question));

        FormActivityDef activity = FormActivityDef.generalFormBuilder("READONLY01", "v1", STUDY_GUID)
                .addName(new Translation("en", "A test activity for verifying the readonly flag"))
                .addSection(body)
                .setListStyleHint(ListStyleHint.NONE)
                .setDisplayOrder(90)
                .setEditTimeoutSec(180L)
                .build();

        TransactionWrapper.useTxn(handle -> {
            long userId = handle.attach(JdbiUser.class).getUserIdByGuid(USER_GUID);
            RevisionMetadata meta = RevisionMetadata.now(userId, "insert sample readonly activity");
            handle.attach(ActivityDao.class).insertActivity(activity, meta);

            assertNotNull(activity.getActivityId());
            LOG.info("Created activity code={} id={} json=\n{}",
                    activity.getActivityCode(), activity.getActivityId(), gson.toJson(activity));
        });
    }
}

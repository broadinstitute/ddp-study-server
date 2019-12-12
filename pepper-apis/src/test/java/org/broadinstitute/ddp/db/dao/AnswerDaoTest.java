package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.AnswerDao;
import org.broadinstitute.ddp.db.DaoException;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityVersionDto;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormSectionDef;
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
import org.broadinstitute.ddp.model.activity.definition.validation.DateRangeRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.LengthRuleDef;
import org.broadinstitute.ddp.model.activity.definition.validation.RequiredRuleDef;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.AnswerRow;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.PicklistRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TemplateType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AnswerDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static AnswerDao oldAnswerDao;
    @org.junit.Rule
    public ExpectedException thrown = ExpectedException.none();
    private String sid;
    private Template prompt;
    private Template placeholder;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            oldAnswerDao = AnswerDao.fromSqlConfig(sqlConfig);
        });
    }

    @Before
    public void refresh() {
        sid = "QID" + Instant.now().toEpochMilli();
        prompt = new Template(TemplateType.TEXT, null, "dummy prompt");
        placeholder = new Template(TemplateType.TEXT, null, "dummy placeholder");
    }

    @Test
    public void testGetAnswerByIdAndType_bool_success() {
        TransactionWrapper.useTxn(handle -> {
            Template trueTmpl = new Template(TemplateType.TEXT, null, "yup");
            Template falseTmpl = new Template(TemplateType.TEXT, null, "nope");
            RequiredRuleDef rule = new RequiredRuleDef(null);
            BoolQuestionDef question = BoolQuestionDef.builder(sid, prompt, trueTmpl, falseTmpl)
                    .addValidation(rule)
                    .build();

            FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
            String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

            BoolAnswer answer = new BoolAnswer(null, sid, null, true);
            String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
            long answerId = handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.BOOLEAN);

            assertEquals(QuestionType.BOOLEAN, returnedAnswer.getQuestionType());
            BoolAnswer boolAnswer = (BoolAnswer) returnedAnswer;
            assertTrue(boolAnswer.getValue());

            handle.rollback();
        });
    }

    private long setUpPicklistTests(Handle handle) {
        Template label = Template.text("picklist label");
        Template opt1Tmpl = Template.text("option 1");
        PicklistOptionDef option1 = new PicklistOptionDef("PO1", opt1Tmpl);
        Template opt2Tmpl = Template.text("option 2");
        Template opt2Details = Template.text("details here");
        PicklistOptionDef option2 = new PicklistOptionDef("PO2", opt2Tmpl, opt2Details);
        RequiredRuleDef rule = new RequiredRuleDef(null);
        PicklistQuestionDef question = PicklistQuestionDef.buildSingleSelect(PicklistRenderMode.DROPDOWN, sid, prompt)
                .setLabel(label)
                .addOption(option1)
                .addOption(option2)
                .addValidation(rule)
                .build();

        FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
        String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

        PicklistAnswer answer = new PicklistAnswer(null, sid, null,
                Arrays.asList(new SelectedPicklistOption("PO1", null)));
        String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
        return handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();
    }

    @Test
    public void testGetAnswerByIdAndType_picklist_success() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpPicklistTests(handle);
            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.PICKLIST);

            assertEquals(QuestionType.PICKLIST, returnedAnswer.getQuestionType());
            PicklistAnswer picklistAnswer = (PicklistAnswer) returnedAnswer;
            assertEquals(1, picklistAnswer.getValue().size());
            assertEquals("PO1", picklistAnswer.getValue().get(0).getStableId());
            assertNull(picklistAnswer.getValue().get(0).getDetailText());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_picklist_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpPicklistTests(handle) + 1;

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find picklist answer with id " + answerId);
            handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.PICKLIST);

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_text_success() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            TextQuestionDef question = TextQuestionDef.builder(TextInputType.TEXT, sid, prompt)
                    .setPlaceholderTemplate(placeholder)
                    .addValidation(rule)
                    .build();

            FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
            String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

            TextAnswer answer = new TextAnswer(null, sid, null, "itsAnAnswer");
            String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
            long answerId = handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.TEXT);

            assertEquals(QuestionType.TEXT, returnedAnswer.getQuestionType());
            TextAnswer textAnswer = (TextAnswer) returnedAnswer;
            assertEquals("itsAnAnswer", textAnswer.getValue());

            handle.rollback();
        });
    }

    private long setUpDateWithPicklistTests(Handle handle) {
        DateRangeRuleDef rule = new DateRangeRuleDef(Template.text("Pi Day to today"),
                LocalDate.of(2018, 3, 14), null, true);
        DatePicklistDef datePicklistDef = new DatePicklistDef(true, 3, 80, null, 1988);
        DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.PICKLIST, sid, prompt)
                .addFields(DateFieldType.DAY, DateFieldType.MONTH, DateFieldType.YEAR)
                .addValidation(rule)
                .setPicklistDef(datePicklistDef)
                .build();

        FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
        String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

        DateAnswer answer = new DateAnswer(null, sid, null, new DateValue(2018, 10, 10));
        String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
        return handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();
    }

    @Test
    public void testGetAnswerByIdAndType_date_picklist_success() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpDateWithPicklistTests(handle);

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.DATE);

            assertEquals(QuestionType.DATE, returnedAnswer.getQuestionType());
            assertEquals(new DateValue(2018, 10, 10), returnedAnswer.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_date_picklist_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpDateWithPicklistTests(handle) + 1;

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find date answer with id " + answerId);
            handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.DATE);

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_date_singleText_success() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .build();

            FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
            String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

            DateAnswer answer = new DateAnswer(null, sid, null, new DateValue(2018, 10, 10));
            String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
            long answerId = handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.DATE);

            assertEquals(QuestionType.DATE, returnedAnswer.getQuestionType());
            assertEquals(new DateValue(2018, 10, 10), returnedAnswer.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_date_text_success() {
        TransactionWrapper.useTxn(handle -> {
            RequiredRuleDef rule = new RequiredRuleDef(null);
            DateQuestionDef question = DateQuestionDef.builder(DateRenderMode.TEXT, sid, prompt)
                    .addFields(DateFieldType.MONTH, DateFieldType.YEAR)
                    .addValidation(rule)
                    .build();

            FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
            String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

            DateAnswer answer = new DateAnswer(null, sid, null, new DateValue(2018, 10, 10));
            String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
            long answerId = handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.DATE);

            assertEquals(QuestionType.DATE, returnedAnswer.getQuestionType());
            assertEquals(new DateValue(2018, 10, 10), returnedAnswer.getValue());

            handle.rollback();
        });
    }

    private long setUpAgreementTests(Handle handle) {
        Template agreeTmpl = Template.text("agreement");
        Template header = Template.text("header");
        Template footer = Template.text("footer");
        RequiredRuleDef rule = new RequiredRuleDef(null);
        AgreementQuestionDef question = new AgreementQuestionDef(sid,
                                                                    false,
                                                                    agreeTmpl,
                                                                    header,
                                                                    footer,
                                                                    Arrays.asList(rule),
                                                                    true);

        FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
        String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

        AgreementAnswer answer = new AgreementAnswer(null, sid, null, true);
        String answerGuid = oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), activityInstanceGuid);
        return handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();
    }

    @Test
    public void testGetAnswerByIdAndType_agreement_success() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpAgreementTests(handle);

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.AGREEMENT);

            assertEquals(QuestionType.AGREEMENT, returnedAnswer.getQuestionType());
            assertTrue((boolean) returnedAnswer.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_agreement_cantFindById() {
        TransactionWrapper.useTxn(handle -> {
            long answerId = setUpAgreementTests(handle) + 1;

            thrown.expect(DaoException.class);
            thrown.expectMessage("Could not find agreement answer with id " + answerId);
            handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.AGREEMENT);

            handle.rollback();
        });
    }

    @Test
    public void testGetAnswerByIdAndType_composite_success() {
        TransactionWrapper.useTxn(handle -> {
            Template datePrompt = new Template(TemplateType.TEXT, null, "date prompt");
            String dateStableId = "CHILD_DATE" + Instant.now().toEpochMilli();
            DateQuestionDef dateQuestion = DateQuestionDef.builder(DateRenderMode.SINGLE_TEXT, dateStableId, datePrompt)
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .setDisplayCalendar(true)
                    .build();

            LengthRuleDef lengthRule = new LengthRuleDef(null, 5, 300);
            Template textPrompt = new Template(TemplateType.TEXT, null, "text prompt");
            String textStableId = "CHILD_TEXT" + Instant.now().toEpochMilli();
            TextQuestionDef textQuestion = TextQuestionDef.builder(TextInputType.TEXT, textStableId, textPrompt)
                    .addValidation(lengthRule)
                    .build();

            Template addButtonTextTemplate = new Template(TemplateType.TEXT, null, "Add Button");
            Template additionalItemTemplate = new Template(TemplateType.TEXT, null, "Another Item");
            CompositeQuestionDef question = CompositeQuestionDef.builder()
                    .setStableId(sid)
                    .setPrompt(prompt)
                    .addChildrenQuestions(dateQuestion, textQuestion)
                    .setAllowMultiple(true)
                    .setAddButtonTemplate(addButtonTextTemplate)
                    .setAdditionalItemTemplate(additionalItemTemplate)
                    .build();

            FormActivityDef formActivityDef = buildSingleSectionForm(testData.getStudyGuid(), question);
            String activityInstanceGuid = buildActivityInstance(handle, formActivityDef);

            DateValue dateValue = new DateValue(2018, 10, 10);
            DateAnswer da = new DateAnswer(null, dateStableId, null, dateValue);
            String textValue = "text!";
            TextAnswer ta = new TextAnswer(null, textStableId, null, textValue);
            CompositeAnswer compAnswer = new CompositeAnswer(null, sid, null);
            compAnswer.addRowOfChildAnswers(da, ta);

            String answerGuid = oldAnswerDao.createAnswer(handle, compAnswer, testData.getUserGuid(), activityInstanceGuid);
            assertNotNull(answerGuid);
            long answerId = handle.attach(JdbiAnswer.class).findDtoByGuid(answerGuid).get().getId();

            Answer returnedAnswer = handle.attach(org.broadinstitute.ddp.db.dao.AnswerDao.class)
                    .getAnswerByIdAndType(answerId, QuestionType.COMPOSITE);

            assertEquals(QuestionType.COMPOSITE, returnedAnswer.getQuestionType());
            CompositeAnswer compositeAnswer = (CompositeAnswer) returnedAnswer;

            assertEquals(1, compositeAnswer.getValue().size());
            List<AnswerRow> childAnswers = compositeAnswer.getValue();
            assertEquals(2, childAnswers.get(0).getValues().size());

            AnswerRow firstRowOfAnswers = childAnswers.get(0);

            assertTrue(firstRowOfAnswers.getValues().get(0) instanceof DateAnswer);
            DateAnswer dateAnswer = (DateAnswer) firstRowOfAnswers.getValues().get(0);
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(0).getAnswerGuid());
            assertEquals(dateValue, dateAnswer.getValue());

            assertTrue(firstRowOfAnswers.getValues().get(1) instanceof TextAnswer);
            TextAnswer textAnswer = (TextAnswer) firstRowOfAnswers.getValues().get(1);
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerId());
            assertNotNull(firstRowOfAnswers.getValues().get(1).getAnswerGuid());
            assertEquals(textValue, textAnswer.getValue());

            handle.rollback();
        });
    }


    private FormActivityDef buildSingleSectionForm(String studyGuid, QuestionDef... questions) {
        return FormActivityDef.generalFormBuilder("ACT" + Instant.now().toEpochMilli(), "v1", studyGuid)
                .addName(new Translation("en", "activity"))
                .addSection(new FormSectionDef(null, TestUtil.wrapQuestions(questions)))
                .build();
    }

    private String buildActivityInstance(Handle handle, FormActivityDef form) {
        ActivityVersionDto version1 = handle.attach(ActivityDao.class)
                .insertActivity(form, RevisionMetadata.now(testData.getUserId(), "test"));
        return TestDataSetupUtil
                .generateTestFormActivityInstanceForUser(handle, version1.getActivityId(), testData.getUserGuid()).getGuid();
    }
}

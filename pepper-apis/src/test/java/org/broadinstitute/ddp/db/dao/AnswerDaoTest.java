package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class AnswerDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static org.broadinstitute.ddp.db.AnswerDao oldAnswerDao;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            oldAnswerDao = org.broadinstitute.ddp.db.AnswerDao.fromSqlConfig(sqlConfig);
        });
    }

    @Test
    public void testFindAnswerById_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = handle.attach(AnswerDao.class).findAnswerById(123456L);
            assertTrue(result.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_agreement() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new AgreementAnswer(null, act.getAgreementQuestion().getStableId(), null, true);
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.AGREEMENT, actual.getQuestionType());
            assertTrue(((AgreementAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_bool() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_text() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "itsAnAnswer");
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.TEXT, actual.getQuestionType());
            assertEquals(answer.getValue(), ((TextAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_date() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withDateFullQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 2018, 10, 24);
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.DATE, actual.getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), actual.getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_numericInteger() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withNumericIntQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new NumericIntegerAnswer(null, act.getNumericIntQuestion().getStableId(), null, 25L);
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.NUMERIC, actual.getQuestionType());
            assertEquals(NumericType.INTEGER, ((NumericAnswer) actual).getNumericType());
            assertEquals((Long) 25L, ((NumericIntegerAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_picklist() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistSingleList(true,
                            new PicklistOptionDef("PO1", Template.text("po1")),
                            new PicklistOptionDef("PO2", Template.text("po2"), Template.text("details")))
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            PicklistAnswer answer = new PicklistAnswer(null, act.getPicklistSingleListQuestion().getStableId(), null,
                    List.of(new SelectedPicklistOption("PO2", "some details")));
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.PICKLIST, actual.getQuestionType());

            var selected = ((PicklistAnswer) actual).getValue();
            assertEquals(1, selected.size());
            assertEquals("PO2", selected.get(0).getStableId());
            assertEquals("some details", selected.get(0).getDetailText());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerById_composite() {
        TransactionWrapper.useTxn(handle -> {
            DateQuestionDef childDate = DateQuestionDef
                    .builder(DateRenderMode.TEXT, "CDATE" + Instant.now().toEpochMilli(), Template.text("child date"))
                    .addFields(DateFieldType.YEAR, DateFieldType.MONTH, DateFieldType.DAY)
                    .build();
            TextQuestionDef childText = TextQuestionDef
                    .builder(TextInputType.TEXT, "CTEXT" + Instant.now().toEpochMilli(), Template.text("child text"))
                    .build();

            TestFormActivity act = TestFormActivity.builder()
                    .withCompositeQuestion(true, childDate, childText)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            CompositeAnswer answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2018, 10, 24)));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 3, 14)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(answer.getAnswerId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.COMPOSITE, actual.getQuestionType());

            var rows = ((CompositeAnswer) actual).getValue();
            assertEquals(2, rows.size());
            assertEquals(1, rows.get(0).getValues().size());
            assertEquals(2, rows.get(1).getValues().size());

            var row1 = rows.get(0).getValues();
            assertEquals(QuestionType.DATE, row1.get(0).getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), row1.get(0).getValue());

            var row2 = rows.get(1).getValues();
            assertEquals(QuestionType.DATE, row2.get(0).getQuestionType());
            assertEquals(new DateValue(2020, 3, 14), row2.get(0).getValue());
            assertEquals(QuestionType.TEXT, row2.get(1).getQuestionType());
            assertEquals("row 2 col 2", row2.get(1).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            String instanceGuid = createInstance(handle, act.getDef().getActivityId());

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            oldAnswerDao.createAnswer(handle, answer, testData.getUserGuid(), instanceGuid);
            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerByGuid(answer.getAnswerGuid())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    private String createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class)
                .insertInstance(activityId, testData.getUserGuid())
                .getGuid();
    }
}

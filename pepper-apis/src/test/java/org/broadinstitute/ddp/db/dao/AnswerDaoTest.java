package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.DaoBuilder;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.exception.OperationNotAllowedException;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AnswerDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private final DaoBuilder<AnswerDao> daoBuilder;
    private boolean isCachedDao;

    public AnswerDaoTest(DaoBuilder daoBuilder, boolean isCachedDao) {
        this.daoBuilder = daoBuilder;
        this.isCachedDao = isCachedDao;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Object[] uncached = {(DaoBuilder<AnswerDao>) (handle) -> handle.attach(AnswerDao.class), false};
        Object[] cached = {(DaoBuilder<AnswerDao>) (handle) -> new AnswerCachedDao(handle), true};
        return List.of(uncached, cached);
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateUpdateDelete_agreement() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new AgreementAnswer(null, act.getAgreementQuestion().getStableId(), null, true));
            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.AGREEMENT, created.getQuestionType());
            assertTrue(((AgreementAnswer) created).getValue());
            AgreementAnswer agreementAnswer = new AgreementAnswer(null, act.getAgreementQuestion().getStableId(), null, false);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), agreementAnswer);

            assertEquals(created.getAnswerId(), agreementAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(agreementAnswer.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();

            assertEquals(created.getAnswerId(), updated.getAnswerId());
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertFalse(((AgreementAnswer) updated).getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_bool() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true));

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.BOOLEAN, created.getQuestionType());
            assertTrue(((BoolAnswer) created).getValue());
            BoolAnswer boolAnswer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, false);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), boolAnswer);

            assertEquals(created.getAnswerId(), boolAnswer.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerId(), updated.getAnswerId());
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertFalse(((BoolAnswer) updated).getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_text() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            TextAnswer textAnswer1 = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "old");
            answerDao.createAnswer(testData.getUserId(), instanceId, textAnswer1);

            assertTrue(textAnswer1.getAnswerId() > 0);
            assertEquals(QuestionType.TEXT, textAnswer1.getQuestionType());
            assertEquals("old", textAnswer1.getValue());
            TextAnswer textAnswer2 = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "new");
            answerDao.updateAnswer(testData.getUserId(), textAnswer1.getAnswerId(), textAnswer2);

            assertEquals(textAnswer1.getAnswerId(), textAnswer2.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(textAnswer1.getAnswerId());
            assertTrue(updatedOpt.isPresent());

            Answer updated = updatedOpt.get();

            assertEquals(textAnswer1.getAnswerId(), updated.getAnswerId());
            assertEquals(textAnswer1.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals("new", updated.getValue());

            answerDao.deleteAnswer(textAnswer1.getAnswerId());
            assertFalse(answerDao.findAnswerById(textAnswer1.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_date() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withDateFullQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            DateAnswer createdAnswer = new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 2018, 10, 24);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId, createdAnswer);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.DATE, created.getQuestionType());
            assertEquals(new DateValue(2018, 10, 24), created.getValue());

            DateAnswer updatedAnswer = new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 2020, 4, null);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updatedAnswer);

            assertEquals(created.getAnswerId(), updatedAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());

            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();

            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals(new DateValue(2020, 4, null), updated.getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_numericInteger() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withNumericIntQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);

            NumericIntegerAnswer created = new NumericIntegerAnswer(null, act.getNumericIntQuestion().getStableId(), null, 25L);
            answerDao.createAnswer(testData.getUserId(), instanceId, created);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.NUMERIC, created.getQuestionType());
            assertEquals(NumericType.INTEGER, ((NumericAnswer) created).getNumericType());
            assertEquals(25L, (long) created.getValue());

            NumericIntegerAnswer updatedNumber = new NumericIntegerAnswer(null, act.getNumericIntQuestion().getStableId(), null, 100L);
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), updatedNumber);

            assertEquals(created.getAnswerId(), updatedNumber.getAnswerId());
            Optional<Answer> updatedOpt = answerDao.findAnswerById(updatedNumber.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            assertEquals(100L, updated.getValue());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_picklist() {
        TransactionWrapper.useTxn(handle -> {
            PicklistOptionDef nestedOptionDef1 = new PicklistOptionDef("NESTED_OPT1", Template.text("nested option 1"));
            PicklistOptionDef nestedOptionDef2 = new PicklistOptionDef("NESTED_OPT2", Template.text("nested option 2"));
            List<PicklistOptionDef> nestedOpts = List.of(nestedOptionDef1, nestedOptionDef2);
            PicklistOptionDef optionDef = new PicklistOptionDef("PARENT_OPT", Template.text("parent option1"),
                    Template.text("nested options Label"), nestedOpts);

            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistMultiList(true,
                            new PicklistOptionDef("PO1", Template.text("po1")),
                            new PicklistOptionDef("PO2", Template.text("po2"), Template.text("details")), optionDef)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId,
                    new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                            new SelectedPicklistOption("PO1"))));

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.PICKLIST, created.getQuestionType());

            var selected = ((PicklistAnswer) created).getValue();
            assertEquals(1, selected.size());
            assertEquals("PO1", selected.get(0).getStableId());

            PicklistAnswer picklistAnswer = new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                    new SelectedPicklistOption("PO2", "details2")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), picklistAnswer);

            assertEquals(created.getAnswerId(), picklistAnswer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(created.getAnswerId());

            assertTrue(updatedOpt.isPresent());

            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());

            selected = ((PicklistAnswer) updated).getValue();
            assertEquals(1, selected.size());
            assertEquals("PO2", selected.get(0).getStableId());
            assertEquals("details2", selected.get(0).getDetailText());

            picklistAnswer = new PicklistAnswer(null, act.getPicklistMultiListQuestion().getStableId(), null, List.of(
                    new SelectedPicklistOption("PARENT_OPT"),
                    new SelectedPicklistOption("NESTED_OPT2")));
            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), picklistAnswer);
            assertEquals(created.getAnswerId(), picklistAnswer.getAnswerId());
            updatedOpt = answerDao.findAnswerById(created.getAnswerId());
            assertTrue(updatedOpt.isPresent());
            updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());
            selected = ((PicklistAnswer) updated).getValue();
            assertEquals(2, selected.size());
            assertEquals("PARENT_OPT", selected.get(0).getStableId());
            assertEquals("NESTED_OPT2", selected.get(1).getStableId());

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateUpdateDelete_composite() {
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
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            // create

            CompositeAnswer answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2018, 10, 24)));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 3, 14)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            var created = answerDao.createAnswer(testData.getUserId(), instanceId, answer);

            assertTrue(created.getAnswerId() > 0);
            assertEquals(QuestionType.COMPOSITE, created.getQuestionType());

            var rows = ((CompositeAnswer) created).getValue();
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

            long row1Child1Id = row1.get(0).getAnswerId();
            long row2Child1Id = row2.get(0).getAnswerId();
            long row2Child2Id = row2.get(1).getAnswerId();

            // update

            answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new TextAnswer(null, childText.getStableId(), null, "row 1 col 2"));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 9, null)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            answerDao.updateAnswer(testData.getUserId(), created.getAnswerId(), answer);

            assertEquals(created.getAnswerId(), answer.getAnswerId());

            Optional<Answer> updatedOpt = answerDao.findAnswerById(answer.getAnswerId());
            assert (updatedOpt.isPresent());

            Answer updated = updatedOpt.get();
            assertEquals(created.getAnswerGuid(), updated.getAnswerGuid());

            rows = ((CompositeAnswer) updated).getValue();
            assertEquals("updated composite should still have same rows", 2, rows.size());
            assertEquals(2, rows.get(0).getValues().size());
            assertEquals(2, rows.get(1).getValues().size());

            row1 = rows.get(0).getValues();
            assertNull("updated composite row 1 col 1 should not have a child answer", row1.get(0));
            assertNotEquals("updated composite row 1 child should be a different answer",
                    (Long) row1Child1Id, row1.get(1).getAnswerId());
            assertEquals(QuestionType.TEXT, row1.get(1).getQuestionType());
            assertEquals("row 1 col 2", row1.get(1).getValue());
            assertFalse("old row 1 child should be deleted", answerDao.findAnswerById(row1Child1Id).isPresent());
            row1Child1Id = row1.get(1).getAnswerId();

            row2 = rows.get(1).getValues();
            assertEquals((Long) row2Child1Id, row2.get(0).getAnswerId());
            assertEquals(QuestionType.DATE, row2.get(0).getQuestionType());
            assertEquals("updated composite row 2 child 1 should be updated",
                    new DateValue(2020, 9, null), row2.get(0).getValue());
            assertEquals((Long) row2Child2Id, row2.get(1).getAnswerId());
            assertEquals(QuestionType.TEXT, row2.get(1).getQuestionType());
            assertEquals("updated composite row 2 child 2 should be the same",
                    "row 2 col 2", row2.get(1).getValue());

            // delete

            answerDao.deleteAnswer(created.getAnswerId());
            assertFalse(answerDao.findAnswerById(created.getAnswerId()).isPresent());
            assertFalse("child answers should be deleted", answerDao.findAnswerById(row1Child1Id).isPresent());
            assertFalse(answerDao.findAnswerById(row2Child1Id).isPresent());
            assertFalse(answerDao.findAnswerById(row2Child2Id).isPresent());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_usingQuestionId() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            Answer actual = daoBuilder.buildDao(handle)
                    .createAnswer(testData.getUserId(), instanceId, act.getBoolQuestion().getQuestionId(), answer);

            assertTrue(actual.getAnswerId() > 0);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCreateAnswer_picklist_detailsNotAllowed() {
        thrown.expect(OperationNotAllowedException.class);
        thrown.expectMessage("PO1 does not allow details");
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistSingleList(true,
                            new PicklistOptionDef("PO1", Template.text("po1")),
                            new PicklistOptionDef("PO2", Template.text("po2"), Template.text("details")))
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            PicklistAnswer answer = new PicklistAnswer(null, act.getPicklistSingleListQuestion().getStableId(), null,
                    List.of(new SelectedPicklistOption("PO1", "some details")));
            daoBuilder.buildDao(handle).createAnswer(testData.getUserId(), instanceId, answer);

            fail("expected exception not thrown");
        });
    }

    @Test
    public void testFindAnswerById_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle).findAnswerById(123456L);
            assertTrue(result.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByGuid_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle).findAnswerByGuid("abcxyz");
            assertTrue(result.isEmpty());
            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            var answerDao = daoBuilder.buildDao(handle);
            String guid = answerDao.createAnswer(testData.getUserId(), instanceId, answer).getAnswerGuid();
            Answer actual = answerDao.findAnswerByGuid(guid).orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = daoBuilder.buildDao(handle);
            Optional<Answer> result = answerDao.findAnswerByInstanceIdAndQuestionStableId(123456L, "abcxyz");
            assertTrue(result.isEmpty());

            result = answerDao.findAnswerByInstanceIdAndQuestionStableId(instanceId, "abcxyz");
            assertTrue(result.isEmpty());

            result = answerDao.findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getBoolQuestion().getStableId());
            assertTrue(result.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_bool() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true);
            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instanceId, answer);
            Answer actual = answerDao
                    .findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getBoolQuestion().getStableId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testFindAnswerByInstanceIdAndQuestionStableId_composite() {
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
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            CompositeAnswer answer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2018, 10, 24)));
            answer.addRowOfChildAnswers(
                    new DateAnswer(null, childDate.getStableId(), null, new DateValue(2020, 3, 14)),
                    new TextAnswer(null, childText.getStableId(), null, "row 2 col 2"));

            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instanceId, answer);
            Answer actual = answerDao
                    .findAnswerByInstanceIdAndQuestionStableId(instanceId, act.getCompositeQuestion().getStableId())
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
    public void testFindAnswerByLatestInstanceAndQuestionStableId_notFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<Answer> result = daoBuilder.buildDao(handle)
                    .findAnswerByLatestInstanceAndQuestionStableId(1L, 2L, "abcxyz");
            assertTrue(result.isEmpty());
        });
    }

    @Test
    public void testFindAnswerByLatestInstanceAndQuestionStableId() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());

            // Create old instance
            long instance1 = createInstance(handle, act.getDef().getActivityId()).getId();
            var answerDao = daoBuilder.buildDao(handle);
            answerDao.createAnswer(testData.getUserId(), instance1,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, false));

            // Create latest instance
            long instance2 = createInstance(handle, act.getDef().getActivityId()).getId();
            answerDao.createAnswer(testData.getUserId(), instance2,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true));

            Answer actual = answerDao.findAnswerByLatestInstanceAndQuestionStableId(
                    testData.getUserId(), testData.getStudyId(), act.getBoolQuestion().getStableId())
                    .orElse(null);

            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue("should be answer from latest instance", ((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    private ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}

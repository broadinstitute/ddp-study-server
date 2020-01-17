package org.broadinstitute.ddp.copy;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.JdbiQuestion;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.QuestionDto;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.model.activity.definition.question.DateQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.PicklistOptionDef;
import org.broadinstitute.ddp.model.activity.definition.question.QuestionDef;
import org.broadinstitute.ddp.model.activity.definition.question.TextQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.FormResponse;
import org.broadinstitute.ddp.model.activity.instance.answer.AgreementAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.DateValue;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.PicklistAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.SelectedPicklistOption;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.DateFieldType;
import org.broadinstitute.ddp.model.activity.types.DateRenderMode;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class AnswerToAnswerCopierTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCopy_noSourceAnswer() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "a", "a", null);
            var sourceQuestion = new QuestionDto(QuestionType.TEXT, 1L, "q1", 1L, 1L, 1L, 1L, false, false, false, 1L, 1L, 1L);
            var targetInstance = new FormResponse(2L, "b", testData.getUserId(), false, 2L, 2L, 2L, "b", "b", null);
            var targetQuestion = new QuestionDto(QuestionType.TEXT, 2L, "q2", 2L, 2L, 2L, 2L, false, false, false, 2L, 2L, 2L);

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertTrue(targetInstance.getAnswers().isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_agreement() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.AGREEMENT, "q1");
            sourceInstance.putAnswer(new AgreementAnswer(1L, "q1", "a", true));

            TestFormActivity act = TestFormActivity.builder()
                    .withAgreementQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getAgreementQuestion());
            var targetSid = act.getAgreementQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.AGREEMENT, actual.getQuestionType());
            assertTrue(((AgreementAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_bool() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.BOOLEAN, "q1");
            sourceInstance.putAnswer(new BoolAnswer(1L, "q1", "a", true));

            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getBoolQuestion());
            var targetSid = act.getBoolQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.BOOLEAN, actual.getQuestionType());
            assertTrue(((BoolAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_date() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.DATE, "q1");
            sourceInstance.putAnswer(new DateAnswer(1L, "q1", "a", 1987, 3, 14));

            TestFormActivity act = TestFormActivity.builder()
                    .withDateFullQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getDateFullQuestion());
            var targetSid = act.getDateFullQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.DATE, actual.getQuestionType());
            assertEquals(new DateValue(1987, 3, 14), ((DateAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_numeric() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.NUMERIC, "q1");
            sourceInstance.putAnswer(new NumericIntegerAnswer(1L, "q1", "a", 35L));

            TestFormActivity act = TestFormActivity.builder()
                    .withNumericIntQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getNumericIntQuestion());
            var targetSid = act.getNumericIntQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.NUMERIC, actual.getQuestionType());
            assertEquals((Long) 35L, ((NumericIntegerAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_text() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.TEXT, "q1");
            sourceInstance.putAnswer(new TextAnswer(1L, "q1", "a", "source-text-value"));

            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getTextQuestion());
            var targetSid = act.getTextQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.TEXT, actual.getQuestionType());
            assertEquals("source-text-value", ((TextAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_picklist() {
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.TEXT, "q1");
            sourceInstance.putAnswer(new PicklistAnswer(1L, "q1", "a", List.of(
                    new SelectedPicklistOption("op1"),
                    new SelectedPicklistOption("op2", "details2"))));

            TestFormActivity act = TestFormActivity.builder()
                    .withPicklistMultiList(true,
                            new PicklistOptionDef("op1", Template.text("")),
                            new PicklistOptionDef("op2", Template.text(""), Template.text("")))
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getPicklistMultiListQuestion());
            var targetSid = act.getPicklistMultiListQuestion().getStableId();

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetSid));

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.PICKLIST, actual.getQuestionType());

            var selected = ((PicklistAnswer) actual).getValue();
            assertEquals(2, selected.size());
            assertEquals("op1", selected.get(0).getStableId());
            assertEquals("op2", selected.get(1).getStableId());
            assertEquals("details2", selected.get(1).getDetailText());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_compositeChild() {
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef childS1 = TextQuestionDef.builder(TextInputType.TEXT, "cs1", Template.text("")).build();
            DateQuestionDef childS2 = DateQuestionDef.builder(DateRenderMode.TEXT, "cs2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act1 = TestFormActivity.builder()
                    .withCompositeQuestion(true, childS1, childS2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var sourceInstance = createInstance(handle, act1.getDef().getActivityId());
            var sourceQuestion1 = getQuestionDto(handle, childS1);
            var sourceQuestion2 = getQuestionDto(handle, childS2);

            CompositeAnswer sourceAnswer = new CompositeAnswer(null, act1.getCompositeQuestion().getStableId(), null);
            sourceAnswer.addRowOfChildAnswers(
                    new TextAnswer(null, "cs1", null, "row 1 col 1"),
                    new DateAnswer(null, "cs2", null, 1987, 3, 14));
            sourceAnswer.addRowOfChildAnswers(List.of());
            sourceAnswer.addRowOfChildAnswers(new TextAnswer(null, "cs1", null, "row 3 col 1"));
            sourceAnswer.addRowOfChildAnswers(new DateAnswer(null, "cs2", null, 2020, 9, null));
            sourceInstance.putAnswer(sourceAnswer);

            TextQuestionDef childT1 = TextQuestionDef.builder(TextInputType.TEXT, "ct1", Template.text("")).build();
            DateQuestionDef childT2 = DateQuestionDef.builder(DateRenderMode.TEXT, "ct2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act2 = TestFormActivity.builder()
                    .withCompositeQuestion(true, childT1, childT2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act2.getDef().getActivityId());
            var targetQuestion1 = getQuestionDto(handle, childT1);
            var targetQuestion2 = getQuestionDto(handle, childT2);
            var targetParentSid = act2.getCompositeQuestion().getStableId();

            var copier = new AnswerToAnswerCopier(handle, testData.getUserId());
            copier.copy(sourceInstance, sourceQuestion1, targetInstance, targetQuestion1);
            copier.copy(sourceInstance, sourceQuestion2, targetInstance, targetQuestion2);

            assertEquals(1, targetInstance.getAnswers().size());
            assertNotNull(targetInstance.getAnswer(targetParentSid));
            assertEquals(4, ((CompositeAnswer) targetInstance.getAnswer(targetParentSid)).getValue().size());

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerById(targetInstance.getAnswer(targetParentSid).getAnswerId()).orElse(null);
            assertNotNull(actual);
            assertEquals(QuestionType.COMPOSITE, actual.getQuestionType());
            var rows = ((CompositeAnswer) actual).getValue();
            assertEquals(4, rows.size());

            var row1 = rows.get(0).getValues();
            assertEquals(2, row1.size());
            assertEquals(QuestionType.TEXT, row1.get(0).getQuestionType());
            assertEquals("row 1 col 1", ((TextAnswer) row1.get(0)).getValue());
            assertEquals(QuestionType.DATE, row1.get(1).getQuestionType());
            assertEquals(new DateValue(1987, 3, 14), ((DateAnswer) row1.get(1)).getValue());

            var row2 = rows.get(1).getValues();
            assertEquals(0, row2.size());

            var row3 = rows.get(2).getValues();
            assertEquals(1, row3.size());
            assertEquals(QuestionType.TEXT, row3.get(0).getQuestionType());
            assertEquals("row 3 col 1", ((TextAnswer) row3.get(0)).getValue());

            var row4 = rows.get(3).getValues();
            assertEquals(2, row4.size());
            assertNull(row4.get(0));
            assertEquals(QuestionType.DATE, row4.get(1).getQuestionType());
            assertEquals(new DateValue(2020, 9, null), ((DateAnswer) row4.get(1)).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testCopy_create_topLevelComposite_notSupported() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("top-level composite"));
        TransactionWrapper.useTxn(handle -> {
            TextQuestionDef childS1 = TextQuestionDef.builder(TextInputType.TEXT, "cs1", Template.text("")).build();
            DateQuestionDef childS2 = DateQuestionDef.builder(DateRenderMode.TEXT, "cs2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act1 = TestFormActivity.builder()
                    .withCompositeQuestion(true, childS1, childS2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var sourceInstance = createInstance(handle, act1.getDef().getActivityId());
            var sourceQuestion = getQuestionDto(handle, act1.getCompositeQuestion());
            sourceInstance.putAnswer(new CompositeAnswer(null, act1.getCompositeQuestion().getStableId(), null));

            TextQuestionDef childT1 = TextQuestionDef.builder(TextInputType.TEXT, "ct1", Template.text("")).build();
            DateQuestionDef childT2 = DateQuestionDef.builder(DateRenderMode.TEXT, "ct2", Template.text(""))
                    .addFields(DateFieldType.MONTH, DateFieldType.DAY, DateFieldType.YEAR).build();
            TestFormActivity act2 = TestFormActivity.builder()
                    .withCompositeQuestion(true, childT1, childT2)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act2.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act2.getCompositeQuestion());

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            fail("expected exception not thrown");
        });
    }

    @Test
    public void testCopy_update_notSupported() {
        thrown.expect(DDPException.class);
        thrown.expectMessage(containsString("updating is currently not supported"));
        TransactionWrapper.useTxn(handle -> {
            var sourceInstance = newDummyInstance();
            var sourceQuestion = newDummyQuestion(QuestionType.BOOLEAN, "q1");
            sourceInstance.putAnswer(new BoolAnswer(1L, "q1", "a", true));

            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            var targetInstance = createInstance(handle, act.getDef().getActivityId());
            var targetQuestion = getQuestionDto(handle, act.getBoolQuestion());
            var targetSid = act.getBoolQuestion().getStableId();
            targetInstance.putAnswer(new BoolAnswer(2L, targetSid, "b", false));

            new AnswerToAnswerCopier(handle, testData.getUserId())
                    .copy(sourceInstance, sourceQuestion, targetInstance, targetQuestion);
            fail("expected exception not thrown");
        });
    }

    private FormResponse newDummyInstance() {
        return new FormResponse(1L, "a", testData.getUserId(), false, 1L, 1L, 1L, "a", "a", null);
    }

    private QuestionDto newDummyQuestion(QuestionType type, String stableId) {
        return new QuestionDto(type, 1L, stableId, 1L, 1L, 1L, 1L, false, false, false, 1L, 1L, 1L);
    }

    private FormResponse createInstance(Handle handle, long activityId) {
        ActivityInstanceDao activityInstanceDao = handle.attach(ActivityInstanceDao.class);
        ActivityInstanceDto instanceDto = activityInstanceDao.insertInstance(activityId, testData.getUserGuid());
        return (FormResponse) activityInstanceDao.findBaseResponseByInstanceId(instanceDto.getId()).get();
    }

    private QuestionDto getQuestionDto(Handle handle, QuestionDef questionDef) {
        return handle.attach(JdbiQuestion.class).getQuestionDtoById(questionDef.getQuestionId()).get();
    }
}

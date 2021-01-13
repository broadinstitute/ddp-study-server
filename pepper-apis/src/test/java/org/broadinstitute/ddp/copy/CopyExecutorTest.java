package org.broadinstitute.ddp.copy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.UserProfileDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.question.NumericQuestionDef;
import org.broadinstitute.ddp.model.activity.definition.template.Template;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.BoolAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.CompositeAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericIntegerAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.NumericType;
import org.broadinstitute.ddp.model.activity.types.QuestionType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.model.copy.CopyPreviousInstanceFilter;
import org.broadinstitute.ddp.model.user.UserProfile;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class CopyExecutorTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testExecute_copyToProfile() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = createInstance(handle, act.getDef().getActivityId()).getId();

            var answer = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "new-first-name");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instanceId, answer);

            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(new CopyConfigurationPair(
                    new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                    new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME))));
            config = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            new CopyExecutor().execute(handle, testData.getUserId(), testData.getUserId(), config);

            UserProfile profile = handle.attach(UserProfileDao.class).findProfileByUserId(testData.getUserId()).get();
            assertEquals("new-first-name", profile.getFirstName());

            handle.rollback();
        });
    }

    @Test
    public void testExecute_copyToAnswer() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act1 = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instance1Id = createInstance(handle, act1.getDef().getActivityId()).getId();

            var answer = new TextAnswer(null, act1.getTextQuestion().getStableId(), null, "source-text");
            handle.attach(AnswerDao.class).createAnswer(testData.getUserId(), instance1Id, answer);

            TestFormActivity act2 = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instance2Id = createInstance(handle, act2.getDef().getActivityId()).getId();

            var config = new CopyConfiguration(testData.getStudyId(), false, List.of(new CopyConfigurationPair(
                    new CopyAnswerLocation(act1.getTextQuestion().getStableId()),
                    new CopyAnswerLocation(act2.getTextQuestion().getStableId()))));
            config = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            new CopyExecutor().execute(handle, testData.getUserId(), testData.getUserId(), config);

            Answer actual = handle.attach(AnswerDao.class)
                    .findAnswerByInstanceIdAndQuestionStableId(instance2Id, act2.getTextQuestion().getStableId())
                    .orElse(null);
            assertNotNull(actual);
            assertNotNull(actual.getAnswerGuid());
            assertEquals("source-text", ((TextAnswer) actual).getValue());

            handle.rollback();
        });
    }

    @Test
    public void testExecute_copyFromPreviousInstance() {
        TransactionWrapper.useTxn(handle -> {
            TestFormActivity act = TestFormActivity.builder()
                    .withBoolQuestion(true)
                    .withTextQuestion(true)
                    .withCompositeQuestion(true, NumericQuestionDef
                            .builder(NumericType.INTEGER, "child-num", Template.text("child-num-prompt"))
                            .build())
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instance1Id = createInstance(handle, act.getDef().getActivityId()).getId();

            AnswerDao answerDao = handle.attach(AnswerDao.class);
            answerDao.createAnswer(testData.getUserId(), instance1Id,
                    new BoolAnswer(null, act.getBoolQuestion().getStableId(), null, true));

            var answer = new TextAnswer(null, act.getTextQuestion().getStableId(), null, "prev-text");
            answerDao.createAnswer(testData.getUserId(), instance1Id, answer);

            var compAnswer = new CompositeAnswer(null, act.getCompositeQuestion().getStableId(), null);
            compAnswer.addRowOfChildAnswers(new NumericIntegerAnswer(null, "child-num", null, 1L));
            compAnswer.addRowOfChildAnswers(new NumericIntegerAnswer(null, "child-num", null, 25L));
            answerDao.createAnswer(testData.getUserId(), instance1Id, compAnswer);

            var config = new CopyConfiguration(testData.getStudyId(), true,
                    List.of(new CopyPreviousInstanceFilter(new CopyAnswerLocation(act.getTextQuestion().getStableId())),
                            new CopyPreviousInstanceFilter(new CopyAnswerLocation(
                                    act.getCompositeQuestion().getChildren().get(0).getStableId()))),
                    List.of());
            config = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            long instance2Id = createInstance(handle, act.getDef().getActivityId()).getId();
            new CopyExecutor()
                    .withTriggeredInstanceId(instance2Id)
                    .execute(handle, testData.getUserId(), testData.getUserId(), config);

            var actual = handle.attach(ActivityInstanceDao.class)
                    .findFormResponseWithAnswersByInstanceId(instance2Id)
                    .orElse(null);
            assertNotNull(actual);
            assertEquals(2, actual.getAnswers().size());
            assertNull("should not copy bool answer since it is not specified",
                    actual.getAnswer(act.getBoolQuestion().getStableId()));

            var actualAnswer = actual.getAnswer(act.getTextQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.TEXT, actualAnswer.getQuestionType());
            assertEquals("prev-text", ((TextAnswer) actualAnswer).getValue());

            actualAnswer = actual.getAnswer(act.getCompositeQuestion().getStableId());
            assertNotNull(actualAnswer);
            assertEquals(QuestionType.COMPOSITE, actualAnswer.getQuestionType());
            var actualCompAnswer = (CompositeAnswer) actualAnswer;
            assertEquals(2, actualCompAnswer.getValue().size());
            assertEquals(1L, actualCompAnswer.getValue().get(0).getValues().get(0).getValue());
            assertEquals(25L, actualCompAnswer.getValue().get(1).getValues().get(0).getValue());

            handle.rollback();
        });
    }

    private ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}

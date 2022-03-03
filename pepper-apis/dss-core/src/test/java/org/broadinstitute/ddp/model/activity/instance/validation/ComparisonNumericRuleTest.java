package org.broadinstitute.ddp.model.activity.instance.validation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComparisonNumericRuleTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Pair<Long, String> questionIdInstanceGuidPair;
    private static Question testQuestion;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        testQuestion = new NumericQuestion("sid", 1L, 2L, false, false, false, null, null, null, List.of(), List.of());
        questionIdInstanceGuidPair = TransactionWrapper.withTxn(ComparisonNumericRuleTest::prepareTestData);
    }

    @Test
    public void testValidateEqual() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.EQUAL)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(20L, questionIdInstanceGuidPair.getRight())));
    }

    @Test
    public void testValidateNotEqual() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.NOT_EQUAL)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(20L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
    }

    @Test
    public void testValidateGreater() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.GREATER)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(20L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
    }

    @Test
    public void testValidateGreaterOrEqual() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.GREATER_OR_EQUAL)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(20L, questionIdInstanceGuidPair.getRight())));
        assertTrue(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(5L, questionIdInstanceGuidPair.getRight())));
    }

    @Test
    public void testValidateLess() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.LESS)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(5L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
    }

    @Test
    public void testValidateLessOrEqual() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .comparisonType(ComparisonType.LESS_OR_EQUAL)
                .build();

        assertTrue(comparisonRule.validate(testQuestion, createAnswer(5L, questionIdInstanceGuidPair.getRight())));
        assertTrue(comparisonRule.validate(testQuestion, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
        assertFalse(comparisonRule.validate(testQuestion, createAnswer(20L, questionIdInstanceGuidPair.getRight())));
    }

    private NumericAnswer createAnswer(long value, String instanceGuid) {
        return new NumericAnswer(null, testData.getStudyGuid(), "abc", value, instanceGuid);
    }

    private static Pair<Long, String> prepareTestData(final Handle handle) {
        TestFormActivity act = TestFormActivity.builder()
                .withNumericIntQuestion(true)
                .build(handle, testData.getUserId(), testData.getStudyGuid());

        var activityInstance = createInstance(handle, act.getDef().getActivityId());

        var created = handle.attach(AnswerDao.class)
                .createAnswer(testData.getUserId(), activityInstance.getId(),
                        new NumericAnswer(null, act.getNumericIntQuestion().getStableId(), null,
                                10L, activityInstance.getGuid()));
        assertTrue(created.getAnswerId() > 0);

        var questionId = handle.attach(QuestionDao.class).getJdbiQuestion()
                .findIdByStableIdAndInstanceGuid(act.getNumericIntQuestion().getStableId(), activityInstance.getGuid());
        assertTrue(questionId.isPresent());

        return ImmutablePair.of(questionId.get(), activityInstance.getGuid());
    }

    private static ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}

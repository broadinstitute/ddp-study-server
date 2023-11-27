package org.broadinstitute.ddp.model.activity.instance.validation;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.QuestionDao;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.model.activity.definition.types.DecimalDef;
import org.broadinstitute.ddp.model.activity.instance.answer.DecimalAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.NumericAnswer;
import org.broadinstitute.ddp.model.activity.instance.question.NumericQuestion;
import org.broadinstitute.ddp.model.activity.instance.question.Question;
import org.broadinstitute.ddp.model.activity.instance.question.TextQuestion;
import org.broadinstitute.ddp.model.activity.types.ComparisonType;
import org.broadinstitute.ddp.model.activity.types.RuleType;
import org.broadinstitute.ddp.model.activity.types.TextInputType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComparisonRuleNegativeTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Pair<Long, String> questionIdInstanceGuidPair;
    private static Question testQuestion;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        testQuestion = new NumericQuestion("sid", 1L, 2L, false, false, false, null, null, null, List.of(), List.of());
        questionIdInstanceGuidPair = TransactionWrapper.withTxn(ComparisonRuleNegativeTest::prepareTestData);
    }

    @Test
    public void testValidate_nothingToValidate() {
        assertTrue(ComparisonRule.builder().build().validate(null, null));
    }

    @Test
    public void testValidate_noValue() {
        assertFalse(ComparisonRule.builder().build().validate(null,
                new NumericAnswer(1L, "q", "a", null)));
    }

    /**
     * This method tests the case when we're trying to use the rule with missing comparison type
     */
    @Test(expected = RuntimeException.class)
    public void testValidate_incorrectComparisonType() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .build();

        comparisonRule.validate(testQuestion, new NumericAnswer(null, testData.getStudyGuid(),
                "abc", 0L, questionIdInstanceGuidPair.getRight()));
    }

    /**
     * This method tests the case when we're trying to compare two answers but one of them is
     * not comparable. For instance, trying to compare TextAnswer to anything else.
     */
    @Test(expected = RuntimeException.class)
    public void testValidate_incorrectQuestionType() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .comparisonType(ComparisonType.EQUAL)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .build();

        comparisonRule.validate((Question) new TextQuestion("sid", 1L,
                2L,  List.of(), List.of(), TextInputType.TEXT),
                new NumericAnswer(null, testData.getStudyGuid(), "abc", 0L,
                        questionIdInstanceGuidPair.getRight()));
    }

    /**
     * This method tests the case when we're trying to compare two answers of comparable questions
     * but having different types. For example, comparing NumericAnswer to DateAnswer.
     */
    @Test(expected = RuntimeException.class)
    public void testValidate_incompatibleQuestions() {
        var comparisonRule = ComparisonRule.builder()
                .type(RuleType.COMPARISON)
                .comparisonType(ComparisonType.EQUAL)
                .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                .build();

        comparisonRule.validate(testQuestion,
                new DecimalAnswer(null, testData.getStudyGuid(), "abc", new DecimalDef(BigInteger.TEN, 0),
                        questionIdInstanceGuidPair.getRight()));
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

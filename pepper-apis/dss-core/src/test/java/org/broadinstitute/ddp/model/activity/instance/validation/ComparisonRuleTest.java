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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ComparisonRuleTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static Question unused;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
        unused = new NumericQuestion("sid", 1L, 2L, false, false, false, null, null, null, List.of(), List.of());
    }

    @Test
    public void testValidate_noValue() {
        ComparisonRule rule = ComparisonRule.builder().build();
        assertFalse(rule.validate(null, null));
        assertTrue(rule.validate(null, new NumericAnswer(1L, "q", "a", null)));
    }

    @Test
    public void testValidateEqual() {
        TransactionWrapper.useTxn(handle -> {
            var questionIdInstanceGuidPair = prepareTestData(handle);
            var comparisonRule = ComparisonRule.builder()
                    .type(RuleType.COMPARISON)
                    .referenceQuestionId(questionIdInstanceGuidPair.getLeft())
                    .comparisonType(ComparisonType.EQUAL)
                    .build();

            assertTrue(comparisonRule.validate(unused, createAnswer(10L, questionIdInstanceGuidPair.getRight())));
            assertFalse(comparisonRule.validate(unused, createAnswer(20L, questionIdInstanceGuidPair.getRight())));

            handle.rollback();
        });
    }

    private NumericAnswer createAnswer(long value, String instanceGuid) {
        return new NumericAnswer(null, testData.getStudyGuid(), "abc", value, instanceGuid);
    }

    private Pair<Long, String> prepareTestData(final Handle handle) {
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

    private ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}

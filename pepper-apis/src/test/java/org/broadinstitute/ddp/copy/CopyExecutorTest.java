package org.broadinstitute.ddp.copy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dto.ActivityInstanceDto;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.instance.answer.Answer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
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

            var config = new CopyConfiguration(testData.getStudyId(), List.of(new CopyConfigurationPair(
                    new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                    new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_FIRST_NAME))));
            config = handle.attach(CopyConfigurationDao.class).createCopyConfig(config);

            new CopyExecutor().execute(handle, testData.getUserId(), testData.getUserId(), config);

            UserProfileDto profile = handle.attach(JdbiProfile.class).getUserProfileByUserId(testData.getUserId());
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

            var config = new CopyConfiguration(testData.getStudyId(), List.of(new CopyConfigurationPair(
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

    private ActivityInstanceDto createInstance(Handle handle, long activityId) {
        return handle.attach(ActivityInstanceDao.class).insertInstance(activityId, testData.getUserGuid());
    }
}

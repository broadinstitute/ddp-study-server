package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.AnswerDao;
import org.broadinstitute.ddp.db.dao.CopyConfigurationDao;
import org.broadinstitute.ddp.db.dao.JdbiProfile;
import org.broadinstitute.ddp.db.dto.UserProfileDto;
import org.broadinstitute.ddp.model.activity.instance.answer.DateAnswer;
import org.broadinstitute.ddp.model.activity.instance.answer.TextAnswer;
import org.broadinstitute.ddp.model.activity.types.InstanceStatusType;
import org.broadinstitute.ddp.model.copy.CopyAnswerLocation;
import org.broadinstitute.ddp.model.copy.CopyConfiguration;
import org.broadinstitute.ddp.model.copy.CopyConfigurationPair;
import org.broadinstitute.ddp.model.copy.CopyLocation;
import org.broadinstitute.ddp.model.copy.CopyLocationType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.broadinstitute.ddp.util.TestFormActivity;
import org.junit.BeforeClass;
import org.junit.Test;

public class CopyAnswerEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCopyAnswerToProfile() {
        TransactionWrapper.useTxn(handle -> {
            JdbiProfile jdbiProfile = handle.attach(JdbiProfile.class);
            UserProfileDto originalProfile = jdbiProfile.getUserProfileByUserId(testData.getUserId());

            TestFormActivity act = TestFormActivity.builder()
                    .withTextQuestion(true)
                    .withDateFullQuestion(true)
                    .build(handle, testData.getUserId(), testData.getStudyGuid());
            long instanceId = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(act.getDef().getActivityId(), testData.getUserGuid()).getId();

            String lastNameFromAnswer = "Sargent" + Instant.now().toEpochMilli();
            var expected = new TextAnswer(null, act.getTextQuestion().getStableId(), null, lastNameFromAnswer);
            AnswerDao answerDao = handle.attach(AnswerDao.class);
            answerDao.createAnswer(testData.getUserId(), instanceId, expected);
            answerDao.createAnswer(testData.getUserId(), instanceId,
                    new DateAnswer(null, act.getDateFullQuestion().getStableId(), null, 1987, 3, 14));

            assertNotEquals("profile last name should start with different values",
                    originalProfile.getLastName(), expected.getValue());

            var config = new CopyConfiguration(testData.getStudyId(), List.of(
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getTextQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_LAST_NAME)),
                    new CopyConfigurationPair(
                            new CopyAnswerLocation(act.getDateFullQuestion().getStableId()),
                            new CopyLocation(CopyLocationType.PARTICIPANT_PROFILE_BIRTH_DATE))));
            long configId = handle.attach(CopyConfigurationDao.class).createCopyConfig(config).getId();

            var signal = new ActivityInstanceStatusChangeSignal(
                    testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    instanceId, act.getDef().getActivityId(),
                    testData.getStudyId(),
                    InstanceStatusType.COMPLETE);
            var action = new CopyAnswerEventAction(null, configId);
            action.doAction(null, handle, signal);

            UserProfileDto profile = jdbiProfile.getUserProfileByUserId(testData.getUserId());
            assertEquals(lastNameFromAnswer, profile.getLastName());
            assertEquals(LocalDate.of(1987, 3, 14), profile.getBirthDate());

            handle.rollback();
        });
    }
}

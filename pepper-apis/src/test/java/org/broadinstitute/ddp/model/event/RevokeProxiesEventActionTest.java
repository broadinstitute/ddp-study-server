package org.broadinstitute.ddp.model.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.UserDao;
import org.broadinstitute.ddp.db.dao.UserGovernanceDao;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.model.activity.types.EventTriggerType;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class RevokeProxiesEventActionTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void test_noProxies() {
        TransactionWrapper.useTxn(handle -> {
            var signal = new EventSignal(testData.getUserId(), testData.getUserId(), testData.getUserGuid(),
                    testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            new RevokeProxiesEventAction(null, null).doAction(null, handle, signal);
            // all good!

            handle.rollback();
        });
    }

    @Test
    public void test_setAllActiveProxiesToInactive() {
        TransactionWrapper.useTxn(handle -> {
            var userDao = handle.attach(UserDao.class);
            User proxy1 = userDao.createUser(testData.getClientId(), "a", null);
            User proxy2 = userDao.createUser(testData.getClientId(), "b", null);
            User participant = userDao.createUser(testData.getClientId(), "c", null);

            var userGovernanceDao = handle.attach(UserGovernanceDao.class);
            long govId1 = userGovernanceDao.assignProxy("proxy1", proxy1.getId(), participant.getId());
            long govId2 = userGovernanceDao.assignProxy("proxy2", proxy2.getId(), participant.getId());
            userGovernanceDao.grantGovernedStudy(govId1, testData.getStudyId());
            userGovernanceDao.grantGovernedStudy(govId2, testData.getStudyId());

            var signal = new EventSignal(participant.getId(), participant.getId(), participant.getGuid(),
                    testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            new RevokeProxiesEventAction(null, null).doAction(null, handle, signal);

            assertEquals(0, userGovernanceDao.findActiveGovernancesByParticipantAndStudyGuids(
                    participant.getGuid(), testData.getStudyGuid()).count());
            assertFalse(userGovernanceDao.findGovernanceById(govId1).get().isActive());

            handle.rollback();
        });
    }

    @Test
    public void test_ProxiesInOtherStudiesAreNotAffected() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);

            var userDao = handle.attach(UserDao.class);
            User proxy1 = userDao.createUser(testData.getClientId(), "a", null);
            User proxy2 = userDao.createUser(testData.getClientId(), "b", null);
            User participant = userDao.createUser(testData.getClientId(), "c", null);

            var userGovernanceDao = handle.attach(UserGovernanceDao.class);
            long govId1 = userGovernanceDao.assignProxy("proxy1", proxy1.getId(), participant.getId());
            long govId2 = userGovernanceDao.assignProxy("proxy2", proxy2.getId(), participant.getId());
            userGovernanceDao.grantGovernedStudy(govId1, testData.getStudyId());
            userGovernanceDao.grantGovernedStudy(govId2, study2.getId());

            var signal = new EventSignal(participant.getId(), participant.getId(), participant.getGuid(),
                    testData.getStudyId(), EventTriggerType.GOVERNED_USER_REGISTERED);
            new RevokeProxiesEventAction(null, null).doAction(null, handle, signal);

            assertEquals(0, userGovernanceDao.findActiveGovernancesByParticipantAndStudyGuids(
                    participant.getGuid(), testData.getStudyGuid()).count());
            assertEquals(1, userGovernanceDao.findActiveGovernancesByParticipantAndStudyGuids(
                    participant.getGuid(), study2.getGuid()).count());
            assertFalse(userGovernanceDao.findGovernanceById(govId1).get().isActive());
            assertTrue(userGovernanceDao.findGovernanceById(govId2).get().isActive());

            handle.rollback();
        });
    }
}

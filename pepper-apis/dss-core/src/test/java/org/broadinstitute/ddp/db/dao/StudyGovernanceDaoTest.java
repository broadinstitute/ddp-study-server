package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.governance.GovernancePolicy;
import org.broadinstitute.ddp.model.pex.Expression;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.junit.BeforeClass;
import org.junit.Test;

public class StudyGovernanceDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreatePolicy() {
        TransactionWrapper.useTxn(handle -> {
            GovernancePolicy policy = new GovernancePolicy(
                    testData.getStudyId(),
                    new Expression("true && true"));

            policy = handle.attach(StudyGovernanceDao.class).createPolicy(policy);
            assertEquals(testData.getStudyId(), policy.getStudyId());
            assertEquals(testData.getStudyGuid(), policy.getStudyGuid());
            assertNotNull(policy.getShouldCreateGovernedUserExpr());
            assertEquals("true && true", policy.getShouldCreateGovernedUserExpr().getText());

            handle.rollback();
        });
    }

    @Test
    public void testFindPolicy() {
        TransactionWrapper.useTxn(handle -> {
            StudyGovernanceDao studyGovernanceDao = handle.attach(StudyGovernanceDao.class);

            GovernancePolicy policy = new GovernancePolicy(
                    testData.getStudyId(),
                    new Expression("true && true"));
            policy = studyGovernanceDao.createPolicy(policy);

            Optional<GovernancePolicy> found = studyGovernanceDao.findPolicyById(policy.getId());
            assertTrue(found.isPresent());
            assertEquals(policy.getStudyId(), found.get().getStudyId());

            found = studyGovernanceDao.findPolicyByStudyId(testData.getStudyId());
            assertTrue(found.isPresent());
            assertEquals(policy.getId(), found.get().getId());
            assertEquals(policy.getStudyId(), found.get().getStudyId());

            found = studyGovernanceDao.findPolicyByStudyGuid(testData.getStudyGuid());
            assertTrue(found.isPresent());
            assertEquals(policy.getId(), found.get().getId());
            assertEquals(policy.getStudyGuid(), found.get().getStudyGuid());

            found = studyGovernanceDao.findPolicyByStudyGuid("not-guid");
            assertTrue(found.isEmpty());

            handle.rollback();
        });
    }
}

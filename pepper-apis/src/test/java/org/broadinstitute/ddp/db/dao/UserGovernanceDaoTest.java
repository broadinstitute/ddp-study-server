package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.cache.CacheService;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.model.governance.Governance;
import org.broadinstitute.ddp.model.user.User;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.sqlobject.SqlObject;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

interface DaoBuilder<T extends SqlObject> {
    T buildDao(Handle handle);
}

@RunWith(Parameterized.class)
public class UserGovernanceDaoTest extends TxnAwareBaseTest {
    private DaoBuilder<UserGovernanceDao> daoBuilder;
    private boolean isCachedDao;

    private static TestDataSetupUtil.GeneratedTestData testData;

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        Object[] uncached = {(DaoBuilder<UserGovernanceDao>)(handle) -> handle.attach(UserGovernanceDao.class), false};
        Object[] cached = {(DaoBuilder<UserGovernanceDao>)(handle) -> new UserGovernanceCachedDao(handle), true};
        return List.of(uncached, cached);
    }

    public UserGovernanceDaoTest(DaoBuilder daoBuilder, boolean isCachedDao) {
        this.daoBuilder = daoBuilder;
        this.isCachedDao = isCachedDao;
    }

    @BeforeClass
    public static void setup() {
        testData = TransactionWrapper.withTxn(TestDataSetupUtil::generateBasicUserTestData);
    }

    @Test
    public void testCreateGovernedUser() {
        TransactionWrapper.useTxn(handle -> {
            Governance gov = daoBuilder.buildDao(handle)
                    .createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertNotNull(gov.getGovernedUserGuid());
            assertEquals("test-alias", gov.getAlias());
            assertEquals(testData.getUserId(), gov.getProxyUserId());
            assertEquals(testData.getUserGuid(), gov.getProxyUserGuid());
            assertTrue(gov.getGrantedStudies().isEmpty());

            User governed = handle.attach(UserDao.class).findUserById(gov.getGovernedUserId()).get();
            assertNull(governed.getAuth0UserId());
            assertEquals(testData.getClientId(), governed.getCreatedByClientId());

            handle.rollback();
        });
    }

    @Test
    public void testCreateGovernedUserWithGuidAlias() {
        TransactionWrapper.useTxn(handle -> {
            Governance gov = daoBuilder.buildDao(handle)
                    .createGovernedUserWithGuidAlias(testData.getClientId(), testData.getUserId());
            assertNotNull(gov.getGovernedUserGuid());
            assertEquals(gov.getGovernedUserGuid(), gov.getAlias());
            assertEquals(testData.getUserId(), gov.getProxyUserId());
            assertEquals(testData.getUserGuid(), gov.getProxyUserGuid());
            assertTrue(gov.getGrantedStudies().isEmpty());

            User governed = handle.attach(UserDao.class).findUserById(gov.getGovernedUserId()).get();
            assertNull(governed.getAuth0UserId());
            assertEquals(testData.getClientId(), governed.getCreatedByClientId());

            handle.rollback();
        });
    }

    @Test
    public void testAssignProxy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = daoBuilder.buildDao(handle);
            Governance gov = userGovernanceDao.createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertTrue(handle.attach(UserDao.class).findUserById(gov.getGovernedUserId()).isPresent());
            assertTrue(userGovernanceDao.findGovernanceById(gov.getId()).isPresent());

            userGovernanceDao.unassignProxy(gov.getId());
            assertFalse(userGovernanceDao.findGovernanceById(gov.getId()).isPresent());

            long newGovId = userGovernanceDao.assignProxy("another-alias", testData.getUserId(), gov.getGovernedUserId());
            Governance newGov = userGovernanceDao.findGovernanceById(newGovId).get();
            assertEquals("another-alias", newGov.getAlias());
            assertEquals(testData.getUserId(), newGov.getProxyUserId());
            assertEquals(gov.getGovernedUserId(), newGov.getGovernedUserId());

            handle.rollback();
        });
    }

    @Test
    public void testGrantGovernedStudy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = daoBuilder.buildDao(handle);
            Governance gov = userGovernanceDao.createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertTrue(gov.getGrantedStudies().isEmpty());

            userGovernanceDao.grantGovernedStudy(gov.getId(), testData.getStudyId());
            Governance newGov = userGovernanceDao.findGovernanceById(gov.getId()).get();
            assertEquals(gov.getAlias(), newGov.getAlias());
            assertEquals(1, newGov.getGrantedStudies().size());
            assertEquals(testData.getStudyGuid(), newGov.getGrantedStudies().get(0).getStudyGuid());

            handle.rollback();
        });
    }

    @Test
    public void testDeleteAllGovernancesForProxy() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = handle.attach(UserGovernanceDao.class);
            Governance gov = userGovernanceDao.createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertEquals(1, userGovernanceDao.findGovernancesByProxyGuid(gov.getProxyUserGuid()).count());

            assertEquals(1, userGovernanceDao.deleteAllGovernancesForProxy(gov.getProxyUserId()));
            assertEquals(0, userGovernanceDao.findGovernancesByProxyGuid(gov.getProxyUserGuid()).count());

            handle.rollback();
        });
    }

    @Test
    public void testFindActiveGovernancesByProxyGuid() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = daoBuilder.buildDao(handle);

            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyGuid("not-guid").count());

            Governance gov = userGovernanceDao.createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertEquals(1, userGovernanceDao.findActiveGovernancesByProxyGuid(testData.getUserGuid()).count());
            userGovernanceDao.disableProxy(gov.getId());
            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyGuid(testData.getUserGuid()).count());
            userGovernanceDao.enableProxy(gov.getId());
            assertEquals(1, userGovernanceDao.findActiveGovernancesByProxyGuid(testData.getUserGuid()).count());

            handle.rollback();
        });
    }

    @Test
    public void testFindActiveGovernancesByProxyAndStudyGuids() {
        TransactionWrapper.useTxn(handle -> {
            UserGovernanceDao userGovernanceDao = daoBuilder.buildDao(handle);

            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids("not-guid", "not-study").count());

            Governance gov = userGovernanceDao.createGovernedUser(testData.getClientId(), testData.getUserId(), "test-alias");
            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids(
                    testData.getUserGuid(), testData.getStudyGuid()).count());

            userGovernanceDao.grantGovernedStudy(gov.getId(), testData.getStudyGuid());
            assertEquals(1, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids(
                    testData.getUserGuid(), testData.getStudyGuid()).count());
            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids(
                    testData.getUserGuid(), "not-study").count());

            userGovernanceDao.disableProxy(gov.getId());
            assertEquals(0, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids(
                    testData.getUserGuid(), testData.getStudyGuid()).count());
            userGovernanceDao.enableProxy(gov.getId());
            assertEquals(1, userGovernanceDao.findActiveGovernancesByProxyAndStudyGuids(
                    testData.getUserGuid(), testData.getStudyGuid()).count());

            handle.rollback();
        });
    }
}

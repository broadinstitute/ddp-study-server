package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class ConsentElectionDaoTest extends TxnAwareBaseTest {

    private static ConsentElectionDao dao;

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String noElectionsActCode;
    private static String twoElectionsActCode;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        dao = new ConsentElectionDao();
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            setupConsentActivities(handle);
        });
    }

    private static void setupConsentActivities(Handle handle) {
        ActivityDao actDao = handle.attach(ActivityDao.class);
        long timestamp = Instant.now().toEpochMilli();

        noElectionsActCode = "CON_ELECT_DAO_NO_ELECTIONS_" + timestamp;
        String consentExpr = "true";
        ConsentActivityDef consent = ConsentActivityDef.builder(noElectionsActCode, "v1", data.getStudyGuid(), consentExpr)
                .addName(new Translation("en", "consent activity with no elections"))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(data.getUserId(), "insert consent with no elections"));
        assertNotNull(consent.getActivityId());

        twoElectionsActCode = "CON_ELECT_DAO_TWO_ELECTIONS_" + timestamp;
        consentExpr = "false";
        consent = ConsentActivityDef.builder(twoElectionsActCode, "v1", data.getStudyGuid(), consentExpr)
                .addName(new Translation("en", "consent activity with two elections"))
                .addElection(new ConsentElectionDef("ELECTION1", "true || false"))
                .addElection(new ConsentElectionDef("ELECTION2", "true && false"))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(data.getUserId(), "insert consent with two elections"));
        assertNotNull(consent.getActivityId());
    }

    @Test
    public void testGetLatestElections_exception() {
        thrown.expect(DaoException.class);

        Handle mockHandle = Mockito.mock(Handle.class, invocationOnMock -> {
            throw new SQLException("testing");
        });

        dao.getLatestElections(mockHandle, noElectionsActCode, data.getStudyId());
    }

    @Test
    public void testGetLatestElections_noneFound() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, "abc", data.getStudyId()));
        assertNotNull(elections);
        assertTrue(elections.isEmpty());
    }

    @Test
    public void testGetLatestElections_noElections() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, noElectionsActCode, data.getStudyId()));
        assertNotNull(elections);
        assertTrue(elections.isEmpty());
    }

    @Test
    public void testGetLatestElections_hasElections() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, twoElectionsActCode, data.getStudyId()));
        assertNotNull(elections);
        assertEquals(2, elections.size());
    }

    @Test
    public void testGetLatestElections_hasStableIds() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, twoElectionsActCode, data.getStudyId()));
        assertEquals(2, elections.size());
        for (ConsentElection election : elections) {
            assertNotNull(election.getStableId());
        }
    }

    @Test
    public void testGetLatestElections_hasExpressions() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, twoElectionsActCode, data.getStudyId()));
        assertEquals(2, elections.size());
        for (ConsentElection election : elections) {
            assertNotNull(election.getSelectedExpr());
        }
    }

    @Test
    public void testGetLatestElections_selectionNotEvaluated() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getLatestElections(handle, twoElectionsActCode, data.getStudyId()));
        assertEquals(2, elections.size());
        for (ConsentElection election : elections) {
            assertNull(election.getSelected());
        }
    }

    @Test
    public void testGetElections_exception() {
        thrown.expect(DaoException.class);

        Handle mockHandle = Mockito.mock(Handle.class, invocationOnMock -> {
            throw new SQLException("testing");
        });

        dao.getElections(mockHandle, noElectionsActCode, "abc", data.getStudyId());
    }

    @Test
    public void testGetElections_activityNotFound() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getElections(handle, "abc", "xyz", data.getStudyId()));
        assertNotNull(elections);
        assertTrue(elections.isEmpty());
    }

    @Test
    public void testGetElections_instanceNotFound() {
        List<ConsentElection> elections = TransactionWrapper.withTxn(
                handle -> dao.getElections(handle, noElectionsActCode, "xyz", data.getStudyId()));
        assertNotNull(elections);
        assertTrue(elections.isEmpty());
    }

    @Test
    public void testGetElections_noElections() {
        TransactionWrapper.useTxn(handle -> {
            long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(data.getStudyId(), noElectionsActCode).get();
            String instanceGuid = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityId, data.getTestingUser().getUserGuid())
                    .getGuid();

            List<ConsentElection> elections = dao.getElections(handle, noElectionsActCode, instanceGuid, data.getStudyId());

            assertNotNull(elections);
            assertTrue(elections.isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetElections_hasElections() {
        TransactionWrapper.useTxn(handle -> {
            long activityId = handle.attach(JdbiActivity.class).findIdByStudyIdAndCode(data.getStudyId(), twoElectionsActCode).get();
            String instanceGuid = handle.attach(ActivityInstanceDao.class)
                    .insertInstance(activityId, data.getTestingUser().getUserGuid())
                    .getGuid();

            List<ConsentElection> elections = dao.getElections(handle, twoElectionsActCode, instanceGuid, data.getStudyId());

            assertNotNull(elections);
            assertEquals(2, elections.size());

            for (ConsentElection election : elections) {
                assertNotNull(election.getStableId());
                assertNotNull(election.getSelectedExpr());
                assertNull(election.getSelected());
                assertTrue(election.getSelectedExpr().matches("true .* false"));
            }

            handle.rollback();
        });
    }
}

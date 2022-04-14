package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.ConsentElectionDao;
import org.broadinstitute.ddp.db.StudyActivityDao;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.ActivityDao;
import org.broadinstitute.ddp.exception.DDPException;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.ConsentElectionDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.instance.ConsentElection;
import org.broadinstitute.ddp.model.activity.revision.RevisionMetadata;
import org.broadinstitute.ddp.pex.PexException;
import org.broadinstitute.ddp.pex.PexInterpreter;
import org.broadinstitute.ddp.pex.TreeWalkInterpreter;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class ConsentServiceTest extends TxnAwareBaseTest {

    private static ConsentService service;
    private static PexInterpreter interpreter;
    private static StudyActivityDao studyActDao;
    private static ConsentElectionDao consentElectionDao;

    private static TestDataSetupUtil.GeneratedTestData data;
    private static String userGuid;
    private static String studyGuid;
    private static String noElectionsActCode;
    private static String hasElectionsActCode;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        interpreter = new TreeWalkInterpreter();
        studyActDao = new StudyActivityDao();
        consentElectionDao = new ConsentElectionDao();
        TransactionWrapper.useTxn(handle -> {
            data = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = data.getUserGuid();
            studyGuid = data.getStudyGuid();
            setupConsentActivities(handle);
        });
    }

    private static void setupConsentActivities(Handle handle) {
        ActivityDao actDao = handle.attach(ActivityDao.class);
        long timestamp = Instant.now().toEpochMilli();

        noElectionsActCode = "CON_SVC_NO_ELECTIONS_" + timestamp;
        String consentExpr = "true";
        ConsentActivityDef consent = ConsentActivityDef.builder(noElectionsActCode, "v1", studyGuid, consentExpr)
                .addName(new Translation("en", "consent activity with no elections"))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(data.getUserId(), "insert consent with no elections"));
        assertNotNull(consent.getActivityId());

        hasElectionsActCode = "CON_SVC_TWO_ELECTIONS_" + timestamp;
        consentExpr = "false";
        consent = ConsentActivityDef.builder(hasElectionsActCode, "v1", studyGuid, consentExpr)
                .addName(new Translation("en", "consent activity with elections"))
                .addElection(new ConsentElectionDef("ELECTION1", "true || false"))
                .addElection(new ConsentElectionDef("ELECTION2", "true && false"))
                .build();
        actDao.insertConsent(consent, RevisionMetadata.now(data.getUserId(), "insert consent with elections"));
        assertNotNull(consent.getActivityId());
    }

    @Before
    public void refresh() {
        service = new ConsentService(interpreter, studyActDao, consentElectionDao);
    }

    @Test
    public void testGetAllConsentSummaries_noneFound() {
        TransactionWrapper.useTxn(handle -> {
            List<ConsentSummary> summaries = service.getAllConsentSummariesByUserGuid(handle, userGuid, userGuid, "abc");
            assertNotNull(summaries);
            assertTrue(summaries.isEmpty());
        });
    }

    @Test
    public void testGetAllConsentSummaries_found() {
        TransactionWrapper.useTxn(handle -> {
            List<ConsentSummary> summaries = service.getAllConsentSummariesByUserGuid(handle, userGuid, userGuid, studyGuid);
            assertNotNull(summaries);
            assertFalse(summaries.isEmpty());

            for (ConsentSummary summary : summaries) {
                if (hasElectionsActCode.equals(summary.getActivityCode())) {
                    assertEquals(2, summary.getElections().size());
                } else if (noElectionsActCode.equals(summary.getActivityCode())) {
                    assertEquals(0, summary.getElections().size());
                } else {
                    fail("Consent activity not recognized: " + summary.getActivityCode());
                }
            }
        });
    }

    @Test
    public void testGetAllConsentSummaries_invalidRevisionedData() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("no consented expression");

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getAllConsentSummaries(any(Handle.class), anyString(), anyString()))
                .thenReturn(Arrays.asList(new ConsentSummary(0, hasElectionsActCode, "some instance guid", null)));

        service = new ConsentService(interpreter, mockDao, consentElectionDao);

        TransactionWrapper.useTxn(handle -> service.getAllConsentSummariesByUserGuid(handle, userGuid, userGuid, studyGuid));
    }

    @Test
    public void testGetAllConsentSummaries_noInstanceNoEval() {
        PexInterpreter mockInterp = Mockito.mock(PexInterpreter.class);
        when(mockInterp.eval(anyString(), any(Handle.class), anyString(), anyString(), anyString()))
                .thenThrow(new PexException("should not be thrown"));

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getAllConsentSummaries(any(Handle.class), anyString(), anyString()))
                .thenReturn(Arrays.asList(new ConsentSummary(0, hasElectionsActCode, null, null)));

        service = new ConsentService(mockInterp, mockDao, consentElectionDao);

        TransactionWrapper.useTxn(handle -> {
            List<ConsentSummary> summaries = service.getAllConsentSummariesByUserGuid(handle, userGuid, userGuid, studyGuid);
            assertNotNull(summaries);
            assertEquals(1, summaries.size());

            ConsentSummary summary = summaries.get(0);
            assertEquals(hasElectionsActCode, summary.getActivityCode());
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsentedExpr());
            assertNull(summary.getConsented());

            List<ConsentElection> elections = summary.getElections();
            assertEquals(2, elections.size());
            for (ConsentElection election : elections) {
                assertNotNull(election.getStableId());
                assertNotNull(election.getSelectedExpr());
                assertNull(election.getSelected());
            }
        });
    }

    @Test
    public void testGetAllConsentSummaries_hasInstanceRunEval() {
        PexInterpreter mockInterp = Mockito.mock(PexInterpreter.class);
        when(mockInterp.eval(anyString(), any(Handle.class), anyString(), anyString(), anyString()))
                .thenReturn(true);

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getAllConsentSummaries(any(Handle.class), anyString(), anyString()))
                .thenReturn(Arrays.asList(new ConsentSummary(0, hasElectionsActCode, "some instance guid", "xyz")));

        ConsentElectionDao mockElectDao = Mockito.mock(ConsentElectionDao.class);
        when(mockElectDao.getElections(any(Handle.class), eq(hasElectionsActCode), eq("some instance guid"), any(Long.class)))
                .thenReturn(Arrays.asList(new ConsentElection("some stable id", "abc")));

        service = new ConsentService(mockInterp, mockDao, mockElectDao);

        TransactionWrapper.useTxn(handle -> {
            List<ConsentSummary> summaries = service.getAllConsentSummariesByUserGuid(handle, userGuid, userGuid, studyGuid);
            assertNotNull(summaries);
            assertEquals(1, summaries.size());

            ConsentSummary summary = summaries.get(0);
            assertEquals(hasElectionsActCode, summary.getActivityCode());
            assertTrue(summary.getConsentedExpr().equals("xyz"));
            assertTrue(summary.getConsented());

            List<ConsentElection> elections = summary.getElections();
            assertEquals(1, elections.size());

            ConsentElection election = elections.get(0);
            assertEquals("some stable id", election.getStableId());
            assertTrue(election.getSelected());
        });
    }

    @Test
    public void testGetConsentSummary_noneFound() {
        TransactionWrapper.useTxn(handle -> {
            Optional<ConsentSummary> summary = service.getLatestConsentSummary(handle, userGuid, userGuid, studyGuid, "abc");
            assertNotNull(summary);
            assertFalse(summary.isPresent());
        });
    }

    @Test
    public void testGetConsentSummary_found() {
        TransactionWrapper.useTxn(handle -> {
            ConsentSummary summary = service.getLatestConsentSummary(handle, userGuid, userGuid, studyGuid, noElectionsActCode).get();
            assertNotNull(summary);
            assertEquals(noElectionsActCode, summary.getActivityCode());
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsentedExpr());
            assertTrue(summary.getElections().isEmpty());
        });
    }

    @Test
    public void testGetConsentSummary_invalidRevisionedData() {
        thrown.expect(DDPException.class);
        thrown.expectMessage("no consented expression");

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getLatestConsentSummary(any(Handle.class), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ConsentSummary(0, hasElectionsActCode, "some instance guid", null)));

        service = new ConsentService(interpreter, mockDao, consentElectionDao);

        TransactionWrapper.useTxn(handle -> service.getLatestConsentSummary(handle, userGuid, userGuid, studyGuid, noElectionsActCode));
    }

    @Test
    public void testGetConsentSummary_noInstanceNoEval() {
        PexInterpreter mockInterp = Mockito.mock(PexInterpreter.class);
        when(mockInterp.eval(anyString(), any(Handle.class), anyString(), anyString(), anyString()))
                .thenThrow(new PexException("should not be thrown"));

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getLatestConsentSummary(any(Handle.class), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ConsentSummary(0, hasElectionsActCode, null, null)));

        service = new ConsentService(mockInterp, mockDao, consentElectionDao);

        TransactionWrapper.useTxn(handle -> {
            ConsentSummary summary = service.getLatestConsentSummary(handle, userGuid, userGuid, studyGuid, hasElectionsActCode).get();
            assertNotNull(summary);

            assertEquals(hasElectionsActCode, summary.getActivityCode());
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsentedExpr());
            assertNull(summary.getConsented());

            assertEquals(2, summary.getElections().size());
            for (ConsentElection election : summary.getElections()) {
                assertNotNull(election.getStableId());
                assertNotNull(election.getSelectedExpr());
                assertNull(election.getSelected());
            }
        });
    }

    @Test
    public void testGetConsentSummary_hasInstanceRunEval() {
        PexInterpreter mockInterp = Mockito.mock(PexInterpreter.class);
        when(mockInterp.eval(anyString(), any(Handle.class), anyString(), anyString(), anyString()))
                .thenReturn(true);

        StudyActivityDao mockDao = Mockito.mock(StudyActivityDao.class);
        when(mockDao.getLatestConsentSummary(any(Handle.class), anyString(), anyString(), anyString()))
                .thenReturn(Optional.of(new ConsentSummary(0, hasElectionsActCode, "some instance guid", "xyz")));

        ConsentElectionDao mockElectDao = Mockito.mock(ConsentElectionDao.class);
        when(mockElectDao.getElections(any(Handle.class), eq(hasElectionsActCode), eq("some instance guid"), any(Long.class)))
                .thenReturn(Arrays.asList(new ConsentElection("some stable id", "abc")));

        service = new ConsentService(mockInterp, mockDao, mockElectDao);

        TransactionWrapper.useTxn(handle -> {
            ConsentSummary summary = service.getLatestConsentSummary(handle, userGuid, userGuid, studyGuid, hasElectionsActCode).get();
            assertNotNull(summary);

            assertEquals(hasElectionsActCode, summary.getActivityCode());
            assertTrue(summary.getConsentedExpr().equals("xyz"));
            assertTrue(summary.getConsented());

            List<ConsentElection> elections = summary.getElections();
            assertEquals(1, elections.size());

            ConsentElection election = elections.get(0);
            assertEquals("some stable id", election.getStableId());
            assertTrue(election.getSelected());
        });
    }
}

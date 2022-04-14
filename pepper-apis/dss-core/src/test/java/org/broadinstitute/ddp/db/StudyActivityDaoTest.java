package org.broadinstitute.ddp.db;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.dao.ActivityInstanceDao;
import org.broadinstitute.ddp.db.dao.ConsentActivityDao;
import org.broadinstitute.ddp.db.dao.FormActivityDao;
import org.broadinstitute.ddp.db.dao.JdbiActivity;
import org.broadinstitute.ddp.db.dao.JdbiClientUmbrellaStudy;
import org.broadinstitute.ddp.db.dao.JdbiRevision;
import org.broadinstitute.ddp.db.dao.JdbiUser;
import org.broadinstitute.ddp.db.dto.StudyDto;
import org.broadinstitute.ddp.json.consent.ConsentSummary;
import org.broadinstitute.ddp.model.activity.definition.ConsentActivityDef;
import org.broadinstitute.ddp.model.activity.definition.FormActivityDef;
import org.broadinstitute.ddp.model.activity.definition.i18n.Translation;
import org.broadinstitute.ddp.model.activity.types.FormType;
import org.broadinstitute.ddp.util.GuidUtils;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mockito;

public class StudyActivityDaoTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static StudyActivityDao dao;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @BeforeClass
    public static void setup() {
        dao = new StudyActivityDao();
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    @Test
    public void testGetAutoInstantiatableActivityIdsByClientIdSuccess() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto study = TestDataSetupUtil.generateTestStudy(handle, cfg);
            handle.attach(JdbiClientUmbrellaStudy.class).insert(testData.getClientId(), study.getId());

            List<Long> ids = dao.getAutoInstantiatableActivityIdsByClientId(handle, testData.getClientId());
            assertTrue(ids.isEmpty());

            ConsentActivityDef act = insertDummyConsent(handle, testData.getUserGuid(), study.getGuid());
            assertEquals(1, handle.attach(JdbiActivity.class).updateAutoInstantiateById(act.getActivityId(), true));

            ids = dao.getAutoInstantiatableActivityIdsByClientId(handle, testData.getClientId());
            assertEquals(1, ids.size());

            handle.rollback();
        });
    }

    @Test
    public void testGetPrequalifierGuidForStudy_studyNotFound() {
        Optional<String> guid = TransactionWrapper.withTxn(
                handle -> dao.getPrequalifierActivityCodeForStudy(handle, "abcxyz"));
        assertNotNull(guid);
        assertFalse(guid.isPresent());
    }

    @Test
    public void testGetPrequalifierGuidForStudy() {
        TransactionWrapper.useTxn(handle -> {
            FormActivityDef act = FormActivityDef.formBuilder(FormType.PREQUALIFIER, "act123", "v1", testData.getStudyGuid())
                    .addName(new Translation("en", "dummy prequal"))
                    .build();
            insertDummyActivity(handle, act, testData.getUserGuid());

            Optional<String> guid = dao.getPrequalifierActivityCodeForStudy(handle, testData.getStudyGuid());
            assertNotNull(guid);
            assertTrue(guid.isPresent());
            assertEquals(act.getActivityCode(), guid.get());

            handle.rollback();
        });
    }

    @Test(expected = DaoException.class)
    public void testGetPrequalifierGuidForStudy_onlyOnePrequalActivityMayExist() {
        TransactionWrapper.useTxn(handle -> {
            for (int displayOrder = 50; displayOrder > 25; displayOrder -= 5) {
                FormActivityDef act = FormActivityDef.formBuilder(
                        FormType.PREQUALIFIER, GuidUtils.randomStandardGuid() + Instant.now().toEpochMilli(), "v1", testData.getStudyGuid()
                ).addName(new Translation("en", "dummy prequal"))
                        .setDisplayOrder(displayOrder)
                        .build();
                insertDummyActivity(handle, act, testData.getUserGuid());
            }

            Optional<String> guid = dao.getPrequalifierActivityCodeForStudy(handle, testData.getStudyGuid());
            handle.rollback();
        });
    }

    @Test
    public void testGetAllConsentSummaries_exception() {
        thrown.expect(DaoException.class);

        Handle mockHandle = Mockito.mock(Handle.class, invocationOnMock -> {
            throw new SQLException("testing");
        });

        dao.getAllConsentSummaries(mockHandle, testData.getUserGuid(), testData.getStudyGuid());
    }

    @Test
    public void testGetAllConsentSummaries_noneFound() {
        List<ConsentSummary> summaries = TransactionWrapper.withTxn(
                handle -> dao.getAllConsentSummaries(handle, testData.getUserGuid(), "abc"));
        assertNotNull(summaries);
        assertTrue(summaries.isEmpty());
    }

    @Test
    public void testGetAllConsentSummaries_foundWhenNoInstancesCreated() {
        TransactionWrapper.useTxn(handle -> {
            ConsentActivityDef act = insertDummyConsent(handle, testData.getUserGuid(), testData.getStudyGuid());

            List<ConsentSummary> summaries = dao.getAllConsentSummaries(handle, testData.getUserGuid(), testData.getStudyGuid());
            assertNotNull(summaries);
            assertEquals(1, summaries.size());

            ConsentSummary summary = summaries.get(0);
            assertEquals(act.getActivityCode(), summary.getActivityCode());
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsentedExpr());
            assertNull(summary.getConsented());
            assertTrue(summary.getElections().isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetAllConsentSummaries_foundWhenHasInstance() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            ConsentActivityDef act = insertDummyConsent(handle, testData.getUserGuid(), testData.getStudyGuid());
            String instanceGuid = instanceDao.insertInstance(act.getActivityId(), testData.getUserGuid()).getGuid();

            List<ConsentSummary> summaries = dao.getAllConsentSummaries(handle, testData.getUserGuid(), testData.getStudyGuid());
            assertNotNull(summaries);
            assertEquals(1, summaries.size());

            ConsentSummary summary = summaries.get(0);
            assertEquals(act.getActivityCode(), summary.getActivityCode());
            assertNull(summary.getConsented());
            assertTrue(summary.getElections().isEmpty());
            assertEquals(instanceGuid, summary.getInstanceGuid());
            assertEquals(act.getConsentedExpr(), summary.getConsentedExpr());

            handle.rollback();
        });
    }

    @Test
    public void testGetAllConsentSummaries_onlyForOneStudy() {
        TransactionWrapper.useTxn(handle -> {
            StudyDto study1 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            StudyDto study2 = TestDataSetupUtil.generateTestStudy(handle, cfg);
            insertDummyConsent(handle, testData.getUserGuid(), study1.getGuid());
            insertDummyConsent(handle, testData.getUserGuid(), study1.getGuid());

            List<ConsentSummary> firstExpected = dao.getAllConsentSummaries(handle, testData.getUserGuid(), study1.getGuid());
            assertEquals(2, firstExpected.size());

            List<ConsentSummary> secondExpected = dao.getAllConsentSummaries(handle, testData.getUserGuid(), study2.getGuid());
            assertTrue(secondExpected.isEmpty());

            ConsentActivityDef consent = insertDummyConsent(handle, testData.getUserGuid(), study2.getGuid());

            List<ConsentSummary> firstActual = dao.getAllConsentSummaries(handle, testData.getUserGuid(), study1.getGuid());
            List<ConsentSummary> secondActual = dao.getAllConsentSummaries(handle, testData.getUserGuid(), study2.getGuid());

            assertEquals(firstExpected.size(), firstActual.size());
            assertFalse(firstActual.stream()
                    .map(ConsentSummary::getActivityCode)
                    .anyMatch(code -> consent.getActivityCode().equals(code)));

            assertEquals(1, secondActual.size());
            assertTrue(secondActual.stream()
                    .map(ConsentSummary::getActivityCode)
                    .allMatch(code -> consent.getActivityCode().equals(code)));

            handle.rollback();
        });
    }

    @Test
    public void testGetConsentSummary_exception() {
        thrown.expect(DaoException.class);

        Handle mockHandle = Mockito.mock(Handle.class, invocationOnMock -> {
            throw new SQLException("testing");
        });

        dao.getLatestConsentSummary(mockHandle, testData.getUserGuid(), testData.getStudyGuid(), "abc");
    }

    @Test
    public void testGetConsentSummary_noneFound() {
        Optional<ConsentSummary> summary = TransactionWrapper.withTxn(
                handle -> dao.getLatestConsentSummary(handle, testData.getUserGuid(), testData.getStudyGuid(), "abc"));
        assertNotNull(summary);
        assertFalse(summary.isPresent());
    }

    @Test
    public void testGetConsentSummary_foundWhenNoInstancesCreated() {
        TransactionWrapper.useTxn(handle -> {
            ConsentActivityDef act = insertDummyConsent(handle, testData.getUserGuid(), testData.getStudyGuid());

            Optional<ConsentSummary> opt = dao.getLatestConsentSummary(handle, testData.getUserGuid(), testData.getStudyGuid(),
                    act.getActivityCode());
            assertTrue(opt.isPresent());

            ConsentSummary summary = opt.get();
            assertEquals(act.getActivityCode(), summary.getActivityCode());
            assertNull(summary.getInstanceGuid());
            assertNull(summary.getConsentedExpr());
            assertNull(summary.getConsented());
            assertTrue(summary.getElections().isEmpty());

            handle.rollback();
        });
    }

    @Test
    public void testGetConsentSummary_foundWhenHasInstance() {
        TransactionWrapper.useTxn(handle -> {
            ActivityInstanceDao instanceDao = handle.attach(ActivityInstanceDao.class);
            ConsentActivityDef act = insertDummyConsent(handle, testData.getUserGuid(), testData.getStudyGuid());
            String instanceGuid = instanceDao.insertInstance(act.getActivityId(), testData.getUserGuid()).getGuid();

            Optional<ConsentSummary> opt = dao.getLatestConsentSummary(handle, testData.getUserGuid(), testData.getStudyGuid(),
                    act.getActivityCode());
            assertTrue(opt.isPresent());

            ConsentSummary summary = opt.get();
            assertEquals(act.getActivityCode(), summary.getActivityCode());
            assertNull(summary.getConsented());
            assertTrue(summary.getElections().isEmpty());
            assertEquals(instanceGuid, summary.getInstanceGuid());
            assertEquals(act.getConsentedExpr(), summary.getConsentedExpr());

            handle.rollback();
        });
    }

    @Test
    public void testGetConsentSummary_onlyForRelatedStudy() {
        TransactionWrapper.useTxn(handle -> {

            StudyDto anotherStudy = TestDataSetupUtil.generateTestStudy(handle, cfg);
            ConsentActivityDef consent = insertDummyConsent(handle, testData.getUserGuid(), anotherStudy.getGuid());
            String guid = consent.getActivityCode();

            Optional<ConsentSummary> summary = dao.getLatestConsentSummary(handle, testData.getUserGuid(), testData.getStudyGuid(), guid);
            assertFalse(summary.isPresent());

            summary = dao.getLatestConsentSummary(handle, testData.getUserGuid(), anotherStudy.getGuid(), guid);
            assertTrue(summary.isPresent());
            assertEquals(guid, summary.get().getActivityCode());

            handle.rollback();
        });
    }

    private void insertDummyActivity(Handle handle, FormActivityDef act, String userGuid) {
        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "add dummy activity");
        handle.attach(FormActivityDao.class).insertActivity(act, revId);
        assertNotNull(act.getActivityId());
    }

    private ConsentActivityDef insertDummyConsent(Handle handle, String userGuid, String studyGuid) {
        long millis = Instant.now().toEpochMilli();
        long userId = handle.attach(JdbiUser.class).getUserIdByGuid(userGuid);
        long revId = handle.attach(JdbiRevision.class).insert(userId, millis, null, "add dummy consent activity");

        String consentExpr = "true";
        ConsentActivityDef consent = ConsentActivityDef.builder("CONSENT" + millis, "v1", studyGuid, consentExpr)
                .addName(new Translation("en", "dummy consent activity"))
                .build();

        handle.attach(ConsentActivityDao.class).insertActivity(consent, revId);
        assertNotNull(consent.getActivityId());
        assertNotNull(consent.getActivityCode());

        return consent;
    }
}

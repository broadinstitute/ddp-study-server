package org.broadinstitute.ddp.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dao.DsmKitRequestDao;
import org.broadinstitute.ddp.db.dao.JdbiCountry;
import org.broadinstitute.ddp.db.dao.JdbiExpression;
import org.broadinstitute.ddp.db.dao.JdbiKitRules;
import org.broadinstitute.ddp.db.dao.JdbiMailAddress;
import org.broadinstitute.ddp.db.dao.JdbiUserStudyEnrollment;
import org.broadinstitute.ddp.db.dao.KitConfigurationDao;
import org.broadinstitute.ddp.db.dao.KitScheduleDao;
import org.broadinstitute.ddp.db.dao.KitTypeDao;
import org.broadinstitute.ddp.db.dto.dsm.DsmKitRequest;
import org.broadinstitute.ddp.db.dto.kit.KitConfigurationDto;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.kit.KitConfiguration;
import org.broadinstitute.ddp.model.kit.KitRuleType;
import org.broadinstitute.ddp.model.kit.KitSchedule;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class KitCheckServiceTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String userGuid;
    private static String studyGuid;
    private static KitConfiguration kitConfig;
    private static long kitConfigExprId;
    private static Set<Long> ruleIdsToDelete = new HashSet<>();

    @BeforeClass
    public static void setUp() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            userGuid = testData.getUserGuid();
            studyGuid = testData.getStudyGuid();
            JdbiMailAddress jdbiAddress = handle.attach(JdbiMailAddress.class);
            jdbiAddress.findDefaultAddressForParticipant(userGuid)
                    .ifPresent(address -> jdbiAddress.deleteAddress(address.getId()));
        });
        setupKitConfiguration();
    }

    private static void setupKitConfiguration() {
        TransactionWrapper.useTxn(handle -> {
            KitConfigurationDao kitConfigDao = handle.attach(KitConfigurationDao.class);
            KitTypeDao kitTypeDao = handle.attach(KitTypeDao.class);
            JdbiKitRules jdbiKitRules = handle.attach(JdbiKitRules.class);
            JdbiCountry jdbiCountry = handle.attach(JdbiCountry.class);
            JdbiExpression jdbiExpr = handle.attach(JdbiExpression.class);

            long kitTypeId = kitTypeDao.getSalivaKitType().getId();
            long kitConfigId = kitConfigDao.insertConfiguration(testData.getStudyId(), 10L, kitTypeId, false);

            String expr = "true";
            kitConfigExprId = jdbiExpr.insertExpression(expr).getId();
            long pexRuleId = jdbiKitRules.insertKitRuleByType(KitRuleType.PEX, kitConfigExprId);
            jdbiKitRules.addRuleToConfiguration(kitConfigId, pexRuleId);
            ruleIdsToDelete.add(pexRuleId);

            for (String countryCode : Arrays.asList("us", "ca")) {
                long countryId = jdbiCountry.getCountryIdByCode(countryCode);
                Long countryRuleId = jdbiKitRules.insertKitRuleByType(KitRuleType.COUNTRY, countryId);
                jdbiKitRules.addRuleToConfiguration(kitConfigId, countryRuleId);
                ruleIdsToDelete.add(countryRuleId);
            }

            KitConfigurationDto kitConfigDto = kitConfigDao.getKitConfigurationDto(kitConfigId);
            kitConfig = kitConfigDao.getKitConfigurationForDto(kitConfigDto);
            assertNotNull(kitConfig);
        });
    }

    @AfterClass
    public static void cleanupKitConfig() {
        TransactionWrapper.useTxn(handle -> {
            KitConfigurationDao kitConfigDao = handle.attach(KitConfigurationDao.class);
            JdbiKitRules jdbiKitRules = handle.attach(JdbiKitRules.class);
            for (long ruleId : ruleIdsToDelete) {
                assertEquals(1, jdbiKitRules.deleteKitRuleByType(kitConfig.getId(), ruleId));
            }
            assertEquals(1, kitConfigDao.deleteConfiguration(kitConfig.getId()));
        });
    }

    @Test
    public void testCheckForKits_userNotEnrolled() {
        TransactionWrapper.useTxn(handle -> {
            JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
            Optional<Long> enrollmentId = jdbiEnrollment.findIdByUserAndStudyGuid(userGuid, studyGuid);
            assertFalse("user should not be enrolled in study yet", enrollmentId.isPresent());

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals("should not have any recipients", 0L, numRecipients);

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_missingMailAddress() {
        TransactionWrapper.useTxn(handle -> {
            JdbiMailAddress jdbiAddress = handle.attach(JdbiMailAddress.class);
            assertFalse("should not have address", jdbiAddress.findDefaultAddressForParticipant(userGuid).isPresent());

            enrollTestUser(handle);

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals("should not have any recipients", 0L, numRecipients);

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_invalidMailAddress() {
        TransactionWrapper.useTxn(handle -> {
            enrollTestUser(handle);

            MailAddress address = createTestMailAddress(handle);
            address.setValidationStatus(DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
            updateTestMailAddress(handle, address);

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals("should not have any recipients", 0L, numRecipients);

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_withUnsupportedCountry() {
        TransactionWrapper.useTxn(handle -> {
            enrollTestUser(handle);

            MailAddress address = createTestMailAddress(handle);
            address.setCountry("UK");   // Contrived example to test error case
            updateTestMailAddress(handle, address);

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals("should not have any recipients", 0L, numRecipients);

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_withSupportedCountry_butFailedEvaluation() {
        TransactionWrapper.useTxn(handle -> {
            enrollTestUser(handle);
            createTestMailAddress(handle);

            JdbiExpression jdbiExpr = handle.attach(JdbiExpression.class);
            assertEquals(1, jdbiExpr.updateById(kitConfigExprId, "false"));

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals("should not have any recipients", 0L, numRecipients);

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_meetsKitCriteria() {
        TransactionWrapper.useTxn(handle -> {
            enrollTestUser(handle);
            createTestMailAddress(handle);

            DsmKitRequestDao kitRequestDao = handle.attach(DsmKitRequestDao.class);
            List<DsmKitRequest> kits = kitRequestDao.findAllKitRequestsForStudy(testData.getStudyGuid());
            assertTrue("should not have any kit requests", kits.isEmpty());

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals(1L, numRecipients);

            kits = kitRequestDao.findAllKitRequestsForStudy(testData.getStudyGuid());
            assertEquals(kitConfig.getNumKits(), kits.size());
            kits.forEach(kit -> assertEquals(userGuid, kit.getParticipantId()));

            handle.rollback();
        });
    }

    @Test
    public void testCheckForKits_userEnrolled_meetsKitCriteria_withCanadaAddress() {
        TransactionWrapper.useTxn(handle -> {
            enrollTestUser(handle);

            MailAddress address = createTestMailAddress(handle);
            address.setStreet1("301 FRONT ST W");
            address.setCity("TORONTO");
            address.setState("ON");
            address.setCountry("CA");
            address.setZip("M5V 2T6");
            updateTestMailAddress(handle, address);

            DsmKitRequestDao kitRequestDao = handle.attach(DsmKitRequestDao.class);
            List<DsmKitRequest> kits = kitRequestDao.findAllKitRequestsForStudy(testData.getStudyGuid());
            assertTrue("should not have any kit requests", kits.isEmpty());

            int numRecipients = new KitCheckService().checkForInitialKits(handle).getTotalNumberOfParticipantsQueuedForKit();
            assertEquals(1L, numRecipients);

            kits = kitRequestDao.findAllKitRequestsForStudy(testData.getStudyGuid());
            assertEquals(kitConfig.getNumKits(), kits.size());
            kits.forEach(kit -> assertEquals(userGuid, kit.getParticipantId()));

            handle.rollback();
        });
    }

    private void enrollTestUser(Handle handle) {
        JdbiUserStudyEnrollment jdbiEnrollment = handle.attach(JdbiUserStudyEnrollment.class);
        jdbiEnrollment.changeUserStudyEnrollmentStatus(userGuid, studyGuid, EnrollmentStatusType.ENROLLED);
    }

    // Use test utils to create a dummy mail address, then pull it out so tests can manage it and cleanup by rolling back.
    private MailAddress createTestMailAddress(Handle handle) {
        TestDataSetupUtil.createTestingMailAddress(handle, testData);
        MailAddress address = testData.getMailAddress();
        testData.setMailAddress(null);
        return address;
    }

    private void updateTestMailAddress(Handle handle, MailAddress address) {
        JdbiMailAddress jdbiAddress = handle.attach(JdbiMailAddress.class);
        assertEquals(1, jdbiAddress.updateAddress(address.getGuid(), address, userGuid, userGuid));
    }

    @Test
    public void testScheduleNextKits() {
        TransactionWrapper.useTxn(TransactionWrapper.DB.APIS, handle -> {
            // Setup
            var kitConfigDao = handle.attach(KitConfigurationDao.class);
            var kitScheduleDao = handle.attach(KitScheduleDao.class);
            var kitRequestDao = handle.attach(DsmKitRequestDao.class);

            long kitTypeId = handle.attach(KitTypeDao.class).getTestBostonKitType().getId();
            long kitConfigId = kitConfigDao.insertConfiguration(testData.getStudyId(), 1, kitTypeId, true);
            kitScheduleDao.createSchedule(new KitSchedule(kitConfigId, 1, "PT1S", null, null, null));

            var addr = createTestMailAddress(handle);
            handle.attach(JdbiUserStudyEnrollment.class).changeUserStudyEnrollmentStatus(
                    testData.getUserGuid(), testData.getStudyGuid(), EnrollmentStatusType.ENROLLED);

            kitRequestDao.createKitRequest(testData.getStudyGuid(), testData.getUserId(), addr.getId(), kitTypeId);
            long recordId = kitScheduleDao.createScheduleRecord(testData.getUserId(), kitConfigId);
            Instant sentTime = Instant.now().minus(1, ChronoUnit.HOURS);
            kitScheduleDao.updateRecordInitialKitSentTime(recordId, sentTime);

            // Run
            var actualResult = new KitCheckService().scheduleNextKits(handle);

            // Verify
            assertNotNull(actualResult);
            assertEquals(1, actualResult.getTotalNumberOfParticipantsQueuedForKit());

            var actualRecord = kitScheduleDao.findRecord(recordId).get();
            assertEquals(1, actualRecord.getNumOccurrences());
            assertNotNull(actualRecord.getLastOccurrenceTime());

            // Cleanup
            handle.rollback();
        });
    }
}

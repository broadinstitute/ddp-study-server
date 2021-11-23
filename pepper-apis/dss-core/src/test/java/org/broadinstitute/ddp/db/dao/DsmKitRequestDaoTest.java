package org.broadinstitute.ddp.db.dao;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.ConfigFile;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.dsm.DsmKitRequest;
import org.broadinstitute.ddp.model.address.MailAddress;
import org.broadinstitute.ddp.model.dsm.KitType;
import org.broadinstitute.ddp.model.user.EnrollmentStatusType;
import org.broadinstitute.ddp.service.AddressService;
import org.broadinstitute.ddp.service.DsmAddressValidationStatus;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.JdbiException;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class DsmKitRequestDaoTest extends TxnAwareBaseTest {
    private static TestDataSetupUtil.GeneratedTestData testData;
    private static String TEST_USER_GUID;
    private static String TEST_STUDY_GUID;
    private DsmKitRequestDao dao;
    private KitTypeDao kitTypeDao;
    private Set<Long> requestKitsIdsToDelete = new HashSet<>();
    private Set<Long> mailAddressesToDelete = new HashSet<>();
    private MailAddress testAddress;

    @BeforeClass
    public static void setupTestUser() {
        TransactionWrapper.useTxn(handle -> {
            testData = TestDataSetupUtil.generateBasicUserTestData(handle);
            TestDataSetupUtil.setUserEnrollmentStatus(handle, testData, EnrollmentStatusType.REGISTERED);
            TEST_USER_GUID = testData.getTestingUser().getUserGuid();
            TEST_STUDY_GUID = testData.getStudyGuid();
        });
    }

    @Before
    public void setUp() {
        //mailDao = new MailAddressDao();
        testAddress = createTestAddress(TEST_USER_GUID);
        mailAddressesToDelete.add(testAddress.getId());
    }

    @Test
    public void testCreateKitRequest() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            kitTypeDao = handle.attach(KitTypeDao.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();
            assertNotNull(salivaKitType);

            Long userId = testData.getUserId();

            long newKitId = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType, true);

            requestKitsIdsToDelete.add(newKitId);

            Optional<DsmKitRequest> kitRequestFromDbOptional = dao.findKitRequest(newKitId);

            assertTrue(kitRequestFromDbOptional.isPresent());
            DsmKitRequest kitRequestFromDb = kitRequestFromDbOptional.get();
            validateKitRequest(kitRequestFromDb, salivaKitType);
            assertTrue(kitRequestFromDb.getNeedsApproval());
            return null;
        });
    }

    @Test
    public void testCreateKitRequestUsingUserGuid() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            kitTypeDao = handle.attach(KitTypeDao.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();
            assertNotNull(salivaKitType);

            long newKitId = dao.createKitRequest(TEST_STUDY_GUID, TEST_USER_GUID, salivaKitType);

            requestKitsIdsToDelete.add(newKitId);

            Optional<DsmKitRequest> kitRequestFromDbOptional = dao.findKitRequest(newKitId);

            assertTrue(kitRequestFromDbOptional.isPresent());
            DsmKitRequest kitRequestFromDb = kitRequestFromDbOptional.get();
            validateKitRequest(kitRequestFromDb, salivaKitType);

            return null;
        });
    }

    @Test
    public void testGetAllKitRequests() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            kitTypeDao = handle.attach(KitTypeDao.class);
            JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();
            KitType bloodKitType = kitTypeDao.getBloodKitType();
            Optional<MailAddress> testAddressOptional = mailDao.findDefaultAddressForParticipant(TEST_USER_GUID);

            Long userId = testData.getUserId();

            List<DsmKitRequest> allExistingRequests = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);
            final Set<String> allExistingRequestGuids = allExistingRequests.stream()
                    .map(r -> r.getKitRequestId()).collect(toSet());
            assertTrue(testAddressOptional.isPresent());
            MailAddress testAddress = testAddressOptional.get();
            List<Long> salivaKitRequestIds = new ArrayList<>();
            List<Long> bloodKitRequestIds = new ArrayList<>();

            long newKitId1 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
            salivaKitRequestIds.add(newKitId1);
            requestKitsIdsToDelete.add(newKitId1);
            long newKitId2 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, bloodKitType);
            bloodKitRequestIds.add(newKitId2);
            requestKitsIdsToDelete.add(newKitId2);
            long newKitId3 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
            salivaKitRequestIds.add(newKitId3);
            requestKitsIdsToDelete.add(newKitId3);
            long newKitId4 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, bloodKitType);
            bloodKitRequestIds.add(newKitId4);
            requestKitsIdsToDelete.add(newKitId4);
            long newKitId5 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
            salivaKitRequestIds.add(newKitId5);
            requestKitsIdsToDelete.add(newKitId5);
            List<DsmKitRequest> allKitsEver = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);

            assertFalse(allKitsEver.isEmpty());

            List<DsmKitRequest> allKits = allKitsEver.stream().filter(k -> !allExistingRequestGuids
                    .contains(k.getKitRequestId())).collect(toList());

            assertEquals(bloodKitRequestIds.size() + salivaKitRequestIds.size(), allKits.size());

            Function<KitType, Predicate<DsmKitRequest>> kitTypeFilterMaker =
                    kitType -> (kr -> kr.getKitType().equals(kitType.getName()));
            List<DsmKitRequest> bloodKitsRequests = allKits.stream()
                    .filter(kitTypeFilterMaker.apply(bloodKitType)).collect(toList());
            List<DsmKitRequest> salivaKitRequests = allKits.stream()
                    .filter(kitTypeFilterMaker.apply(salivaKitType)).collect(toList());

            assertEquals(bloodKitRequestIds.size(), bloodKitsRequests.size());
            assertEquals(salivaKitRequestIds.size(), salivaKitRequests.size());
            for (int i = 0; i < salivaKitRequestIds.size(); i++) {
                validateKitRequest(salivaKitRequests.get(i), salivaKitType);
            }
            for (int i = 0; i < bloodKitRequestIds.size(); i++) {
                validateKitRequest(bloodKitsRequests.get(i), bloodKitType);
            }

            Optional<DsmKitRequest> kit1Request = dao.findKitRequest(newKitId1);
            List<DsmKitRequest> kitsAfterKit1 = dao.findKitRequestsAfterGuid(TEST_STUDY_GUID,
                    kit1Request.get().getKitRequestId());
            assertEquals(allKits.size() - 1, kitsAfterKit1.size());
            Set<Long> idsOfKitsAfterKit1 = kitsAfterKit1.stream().map(DsmKitRequest::getId).collect(toSet());
            assertFalse(idsOfKitsAfterKit1.contains(newKitId1));

            Optional<DsmKitRequest> kit1Request5 = dao.findKitRequest(newKitId5);
            assertTrue(dao.findKitRequestsAfterGuid(TEST_STUDY_GUID, kit1Request5.get().getKitRequestId()).isEmpty());
            return null;
        });
    }

    public void setupTestGetAllKitRequestsWithStatusTests(Handle handle) {
        dao = handle.attach(DsmKitRequestDao.class);
        kitTypeDao = handle.attach(KitTypeDao.class);
        JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
        KitType salivaKitType = kitTypeDao.getSalivaKitType();

        Long userId = testData.getUserId();

        testAddress.setValidationStatus(DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS);
        mailDao.updateAddress(testAddress.getGuid(), testAddress, TEST_USER_GUID, TEST_USER_GUID);

        long newKitId = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
        requestKitsIdsToDelete.add(newKitId);
    }

    @Test
    public void testGetAllKitRequestsWithStatus() {
        TransactionWrapper.useTxn(handle -> {
            setupTestGetAllKitRequestsWithStatusTests(handle);
            List<DsmKitRequest> allNewKits = dao.findAllKitRequestsForStudyWithStatus(TEST_STUDY_GUID,
                    DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS);
            assertEquals(1, allNewKits.size());
        });
    }

    @Test
    public void testGetAllKitRequestsWithStatus_failure() {
        TransactionWrapper.useTxn(handle -> {
            setupTestGetAllKitRequestsWithStatusTests(handle);

            List<DsmKitRequest> allNewKits = dao.findAllKitRequestsForStudyWithStatus(TEST_STUDY_GUID,
                    DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
            assertEquals(0, allNewKits.size());
        });
    }

    @Test
    public void testGetKitRequestsAfterGuid() {
        TransactionWrapper.useTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            kitTypeDao = handle.attach(KitTypeDao.class);
            JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();

            List<DsmKitRequest> allNewKits = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);
            assertEquals(0, allNewKits.size());

            Long userId = testData.getUserId();

            long newKitId = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
            requestKitsIdsToDelete.add(newKitId);

            String mostRecentRequestGuid = dao.findKitRequest(newKitId).get().getKitRequestId();
            allNewKits = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);
            assertEquals(1, allNewKits.size());

            testAddress.setValidationStatus(DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
            mailDao.updateAddress(testAddress.getGuid(), testAddress, TEST_USER_GUID, TEST_USER_GUID);

            long newKitId1 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
            requestKitsIdsToDelete.add(newKitId1);

            allNewKits = dao.findKitRequestsAfterGuid(TEST_STUDY_GUID, mostRecentRequestGuid);
            assertEquals(1, allNewKits.size());
        });
    }

    public String setupGetKitRequestsAfterGuidWithStatusTests(Handle handle) {
        dao = handle.attach(DsmKitRequestDao.class);
        kitTypeDao = handle.attach(KitTypeDao.class);
        JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
        KitType salivaKitType = kitTypeDao.getSalivaKitType();

        List<DsmKitRequest> allNewKits = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);
        assertEquals(0, allNewKits.size());

        Long userId = testData.getUserId();

        long newKitId = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
        requestKitsIdsToDelete.add(newKitId);

        String mostRecentRequestGuid = dao.findKitRequest(newKitId).get().getKitRequestId();
        allNewKits = dao.findAllKitRequestsForStudy(TEST_STUDY_GUID);
        assertEquals(1, allNewKits.size());

        testAddress.setValidationStatus(DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
        mailDao.updateAddress(testAddress.getGuid(), testAddress, TEST_USER_GUID, TEST_USER_GUID);

        long newKitId1 = dao.createKitRequest(TEST_STUDY_GUID, testAddress, userId, salivaKitType);
        requestKitsIdsToDelete.add(newKitId1);

        return mostRecentRequestGuid;
    }

    @Test
    public void testGetKitRequestsAfterGuidWithStatus() {
        TransactionWrapper.useTxn(handle -> {
            String mostRecentRequestGuid = setupGetKitRequestsAfterGuidWithStatusTests(handle);
            List<DsmKitRequest> allNewKits = dao.findKitRequestsAfterGuidWithStatus(TEST_STUDY_GUID,
                    mostRecentRequestGuid,
                    DsmAddressValidationStatus.DSM_INVALID_ADDRESS_STATUS);
            assertEquals(1, allNewKits.size());
        });
    }

    @Test
    public void testGetKitRequestsAfterGuidWithStatus_failure() {
        TransactionWrapper.useTxn(handle -> {
            String mostRecentRequestGuid = setupGetKitRequestsAfterGuidWithStatusTests(handle);
            List<DsmKitRequest> allNewKits = dao.findKitRequestsAfterGuidWithStatus(TEST_STUDY_GUID,
                    mostRecentRequestGuid,
                    DsmAddressValidationStatus.DSM_VALID_ADDRESS_STATUS);
            assertEquals(0, allNewKits.size());
        });
    }

    @Test
    public void testDuplicateGuidRequest() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            kitTypeDao = handle.attach(KitTypeDao.class);
            JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
            KitType salivaKitType = kitTypeDao.getSalivaKitType();
            Optional<MailAddress> testAddressOptional = mailDao.findDefaultAddressForParticipant(TEST_USER_GUID);
            Long userId = testData.getUserId();
            try {
                long newKitId1 = dao.createKitRequest("DUPLICATEGUID", TEST_STUDY_GUID,
                        testAddressOptional.get().getId(), salivaKitType.getId(), userId, Instant.now().getEpochSecond(), false);
                requestKitsIdsToDelete.add(newKitId1);
                long duplicate = dao.createKitRequest("DUPLICATEGUID", TEST_STUDY_GUID,
                        testAddressOptional.get().getId(), salivaKitType.getId(), userId, Instant.now().getEpochSecond(), false);
            } catch (JdbiException e) {
                boolean isExceptionCausedByUniqueConstraint = e.getCause().getMessage()
                        .toLowerCase().contains("kit_request_guid_uk");
                assertTrue(isExceptionCausedByUniqueConstraint);
                return null;
            }
            fail("The exception did not occur. Test failed!");
            return null;
        });
    }

    private void validateKitRequest(DsmKitRequest kitRequestFromDb, KitType salivaKitType) {
        assertEquals(salivaKitType.getName(), kitRequestFromDb.getKitType());
        assertEquals(TEST_USER_GUID, kitRequestFromDb.getParticipantId());
        assertNotNull(kitRequestFromDb.getKitRequestId());
    }

    private MailAddress createTestAddress(String userGuid) {
        MailAddress testAddress = new MailAddress();
        testAddress.setName("Richard Crenna");
        testAddress.setStreet1("415 Main Street");
        testAddress.setCity("Cambridge");
        testAddress.setState("MA");
        testAddress.setCountry("US");
        testAddress.setZip("02142");
        testAddress.setPhone("999-232-5522");
        testAddress.setDefault(true);
        AddressService addressService = new AddressService(cfg.getString(ConfigFile.EASY_POST_API_KEY),
                cfg.getString(ConfigFile.GEOCODING_API_KEY));
        return TransactionWrapper.withTxn(handle -> addressService.addAddress(handle, testAddress, TEST_USER_GUID, TEST_USER_GUID));
    }

    @After
    public void breakDown() {
        TransactionWrapper.withTxn(handle -> {
            dao = handle.attach(DsmKitRequestDao.class);
            JdbiMailAddress mailDao = handle.attach(JdbiMailAddress.class);
            requestKitsIdsToDelete.forEach(id -> dao.deleteKitRequest(id));
            mailAddressesToDelete.forEach(id -> mailDao.deleteAddress(id));
            return null;
        });

    }
}

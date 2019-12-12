package org.broadinstitute.ddp.db.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Optional;

import org.broadinstitute.ddp.TxnAwareBaseTest;
import org.broadinstitute.ddp.constants.TestConstants;
import org.broadinstitute.ddp.db.TransactionWrapper;
import org.broadinstitute.ddp.db.dto.MedicalProviderDto;
import org.broadinstitute.ddp.util.TestDataSetupUtil;
import org.jdbi.v3.core.Handle;
import org.junit.BeforeClass;
import org.junit.Test;

public class JdbiMedicalProviderTest extends TxnAwareBaseTest {

    private static TestDataSetupUtil.GeneratedTestData testData;

    @BeforeClass
    public static void setup() {
        TransactionWrapper.useTxn(handle -> testData = TestDataSetupUtil.generateBasicUserTestData(handle));
    }

    private Optional<MedicalProviderDto> getTestMedicalProvider(Handle handle) {
        return handle.attach(JdbiMedicalProvider.class).getByGuid(testData.getMedicalProvider().getUserMedicalProviderGuid());
    }

    @Test
    public void testGetByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, testData);
            Optional<MedicalProviderDto> medicalProvider = getTestMedicalProvider(handle);
            assertNotNull(medicalProvider);
            assertTrue(medicalProvider.isPresent());
            assertEquals(testData.getMedicalProvider().getUserMedicalProviderGuid(), medicalProvider.get().getUserMedicalProviderGuid());
            assertEquals(testData.getUserId(), medicalProvider.get().getUserId());
            assertEquals(TestConstants.TEST_INSTITUTION_NAME, medicalProvider.get().getInstitutionName());
            assertEquals(TestConstants.TEST_INSTITUTION_PHYSICIAN_NAME, medicalProvider.get().getPhysicianName());
            assertEquals(TestConstants.TEST_INSTITUTION_CITY, medicalProvider.get().getCity());
            assertEquals(TestConstants.TEST_INSTITUTION_STATE, medicalProvider.get().getState());
            TestDataSetupUtil.deleteTestMedicalProvider(handle, testData);
        });
    }

    @Test
    public void testGetAllByUserGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, testData);
            List<MedicalProviderDto> userMedicalProviders = handle.attach(JdbiMedicalProvider.class)
                    .getAllByUserGuidStudyGuidAndInstitutionTypeId(
                            testData.getUserGuid(),
                            testData.getStudyGuid(),
                            handle.attach(JdbiInstitutionType.class).getIdByType(TestConstants.TEST_INSTITUTION_TYPE).orElse(null)
                    );
            assertNotNull(userMedicalProviders);
            boolean newGuidFound = userMedicalProviders.stream()
                    .filter(p -> p.getUserMedicalProviderGuid().equals(testData.getMedicalProvider().getUserMedicalProviderGuid()))
                    .count() == 1;
            assertTrue(newGuidFound);
            TestDataSetupUtil.deleteTestMedicalProvider(handle);
        });
    }

    @Test
    public void testInsert() {
        TransactionWrapper.useTxn(handle -> {
            int medicalProviderId = TestDataSetupUtil.createTestMedicalProvider(handle, testData);
            assertFalse(medicalProviderId == 0);
            TestDataSetupUtil.deleteTestMedicalProvider(handle, testData);
        });
    }

    @Test
    public void testUpdateByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, testData);
            handle.attach(JdbiMedicalProvider.class).updateByGuid(
                    new MedicalProviderDto(
                            null,
                            testData.getMedicalProvider().getUserMedicalProviderGuid(),
                            testData.getUserId(),
                            testData.getStudyId(),
                            TestConstants.TEST_INSTITUTION_TYPE,
                            TestConstants.TEST_INSTITUTION_NAME,
                            TestConstants.TEST_INSTITUTION_PHYSICIAN_NAME,
                            TestConstants.TEST_INSTITUTION_CITY,
                            TestConstants.TEST_INSTITUTION_STATE.toUpperCase(),
                            null,
                            null,
                            null,
                            null
                    )
            );
            Optional<MedicalProviderDto> medicalProvider = getTestMedicalProvider(handle);
            assertEquals(TestConstants.TEST_INSTITUTION_STATE.toUpperCase(), medicalProvider.get().getState());
            TestDataSetupUtil.deleteTestMedicalProvider(handle, testData);
        });
    }

    @Test
    public void testDeleteByGuid() {
        TransactionWrapper.useTxn(handle -> {
            TestDataSetupUtil.createTestMedicalProvider(handle, testData);
            TestDataSetupUtil.deleteTestMedicalProvider(handle, testData);
            Optional<MedicalProviderDto> medicalProvider = getTestMedicalProvider(handle);
            assertFalse(medicalProvider.isPresent());
        });
    }
}
